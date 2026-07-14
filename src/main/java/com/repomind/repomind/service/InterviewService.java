package com.repomind.repomind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.dto.request.InterviewRequest;
import com.repomind.repomind.dto.response.InterviewQuestionDto;
import com.repomind.repomind.dto.response.InterviewSessionDto;
import com.repomind.repomind.model.entity.*;
import com.repomind.repomind.repository.*;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import jakarta.persistence.SecondaryTable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InterviewService {

    private final ChatClient chatClient;
    private final RepoJpaRepository repoRepository;
    private final CodeChunkRepository chunkRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final UserRepoRepository userRepoRepository;

    public InterviewService(
            @Qualifier("structuredChatClient") ChatClient chatClient,
            RepoJpaRepository repoRepository,
            CodeChunkRepository chunkRepository,
            InterviewSessionRepository sessionRepository,
            InterviewQuestionRepository questionRepository,
            ObjectMapper objectMapper,
            EmbeddingService embeddingService,
            UserRepoRepository userRepoRepository)
    {
        this.chatClient = chatClient;
        this.repoRepository = repoRepository;
        this.chunkRepository = chunkRepository;
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.userRepoRepository = userRepoRepository;
    }


    public InterviewSessionDto generateQuestions(InterviewRequest request , User currentUser){

        // Load the repo and verify it exists and is ready
        UserRepo userRepo = userRepoRepository.findByUserIdAndRepoId(currentUser.getId(), request.getRepoId())
                .orElseThrow(() -> new RuntimeException("Access denied"));
        RepoEntity repo = userRepo.getRepo();
        if(repo.getStatus() != RepoEntity.IngestionStatus.READY){
            throw  new RuntimeException("Repository is not ready yet");
        }

        // Build a repo summary to give the LLM context about what project this is
        // We sample chunks from the repo rather than sending everything
        // because the LLM has context window limit
        String repoSummary = buildRepoSummary(repo, request.getDifficulty());

        // Build the prompt - this is the most critical part of this entire feature
        // The quality of questions depends entirely on prompt quality
        String prompt = buildInterviewPrompt(repoSummary,repo.getRepoName(),request.getDifficulty());

        // Call LLM and get raw response
        log.info("Generating {} interview questions for repo: {}",request.getDifficulty(),repo.getRepoName());
        String rawResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        // Parse the JSON response into Java objects
        List<ParsedQuestion> parsedQuestions = parsedQuestionsFromResponse(rawResponse);

        // Save the session and questions to database
        InterviewSession session = InterviewSession.builder()
                .repository(repo)
                .user(currentUser)
                .difficulty(request.getDifficulty())
                .build();

        session = sessionRepository.save(session);

        List<InterviewQuestion> savedQuestions = new ArrayList<>();
        for(int i = 0 ; i<parsedQuestions.size(); i++){
            ParsedQuestion pq = parsedQuestions.get(i);
            InterviewQuestion q = InterviewQuestion.builder()
                    .session(session)
                    .question(pq.question())
                    .expectedAnswer(pq.expectedAnswer())
                    .difficulty(request.getDifficulty())
                    .conceptTested(pq.conceptTested())
                    .questionOrder(i+1)
                    .build();
            savedQuestions.add(questionRepository.save(q));
        }
        return toDto(session,savedQuestions,repo);
    }
    public InterviewSessionDto getSession(UUID sessionId , User currentUser){
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(()-> new RuntimeException("Session not found"));

        // Security check - users can only see their own sessions
        if(!session.getUser().getId().equals(currentUser.getId())){
            throw new RuntimeException("Access denied");
        }

        List<InterviewQuestion> questions = questionRepository
                .findBySessionIdOrderByQuestionOrderAsc(sessionId);

        return toDto(session,questions,session.getRepository());
    }

    public List<InterviewSessionDto> getUserSessions(User currentUser){
        return sessionRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(session -> {
                    List<InterviewQuestion> questions = questionRepository
                            .findBySessionIdOrderByQuestionOrderAsc(session.getId());
                    return toDto(session,questions,session.getRepository());
                })
                .toList();
    }


    // Builds a concise summary of the repo to include in the prompt
    // We cannot send all chunks — too many tokens
    // Instead we take a sample: up to 20 chunks from different files
    // This gives the LLM enough to understand the project structure
    private String buildRepoSummary(RepoEntity repo,String difficulty) {
        long totalChunks = chunkRepository.countByRepositoryId(repo.getId());

        // Embed a meaningful query that describes what we want to find
        // "main service architecture entry point" will semantically match
        // the most important structural files in any codebase:
        // - Main application class
        // - Primary service classes
        // - Core controllers
        // - Key entity/model classes
        // This gives the LLM the RIGHT files to base questions on
        // Use difficulty-aware embedding for better context selection
        float[] summaryEmbedding = buildDifficultyAwareEmbedding(difficulty);
        String vectorString = embeddingService.toVectorString(summaryEmbedding);

        List<CodeChunkRepository.CodeChunkProjection> sampleChunks = chunkRepository.findTopSimilarChunks(
                repo.getId(),
                vectorString,
                20
        );

        Set<String> uniqueFiles = sampleChunks.stream()
                .map(CodeChunkRepository.CodeChunkProjection::getFilePath)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder summary = new StringBuilder();
        summary.append("Repository: ").append(repo.getRepoName()).append("\n");
        summary.append("Total chunks indexed: ").append(totalChunks).append("\n\n");

        summary.append("Key files in this project:\n");
        uniqueFiles.forEach(f -> summary.append("  - ").append(f).append("\n"));

        summary.append("\nSample code context:\n");
        sampleChunks.stream().limit(5).forEach(chunk ->
                summary.append("\n")
                        .append(chunk.getContent(), 0, Math.min(500, chunk.getContent().length()))
                        .append("...\n")
        );

        return summary.toString();
    }
    private float[] buildDifficultyAwareEmbedding(String difficulty) {
        String query = switch (difficulty) {
            // Beginner questions are about what things do — get entry points and main flows
            case "BEGINNER" -> "main application entry point basic flow request response";

            // Intermediate questions are about how things work — get implementation details
            case "INTERMEDIATE" -> "service implementation business logic authentication database";

            // Advanced questions are about design decisions — get config, security, architecture
            case "ADVANCED" -> "security configuration architecture design pattern performance optimization";

            default -> "main service architecture overview";
        };

        return embeddingService.embed(query);
    }
    private String buildInterviewPrompt(String repoSummary , String repoName , String difficulty){
        // Difficulty-specific instructions
        String difficultyInstructions = switch(difficulty){
            case "BEGINNER" -> """
                Questions should focus on:
                - What each main class or file does
                - Basic flow of the application (how a request travels through the code)
                - Simple concepts like what the database stores, what the API does
                - Questions a junior developer would be asked about this project
                """;
            case "INTERMEDIATE" -> """
                Questions should focus on:
                - Design decisions made in the codebase (why certain patterns were chosen)
                - How specific features are implemented (authentication, data flow)
                - Error handling and edge cases visible in the code
                - Questions a mid-level developer would be asked about this project
                """;
            case "ADVANCED" -> """
                    Questions should focus on:
                    - Scalability concerns with the current architecture
                    - Security vulnerabilities or improvements possible
                    - Performance optimizations and their tradeoffs
                    - System design alternatives to what was implemented
                    - Questions a senior developer would be asked about this project
                    """;
            default -> "";
        };
        return """
                You are a senior technical interviewer preparing questions for a project called: %s
                
                PROJECT DETAILS:
                %s
                DIFFICULTY LEVEL: %s
                %s
    
                Generate exactly 5 interview questions specific to THIS project.
                The questions must reference actual files, classes, or patterns visible in the code above.
                Do NOT generate generic programming questions.
    
                CRITICAL: Respond with ONLY a valid JSON array. No explanation. No markdown. No code blocks.
                Start your response with [ and end with ].
    
                Use exactly this JSON structure:
                [
                  {
                    "question": "Specific question about this project",
                    "expectedAnswer": "Detailed answer based on the actual code",
                    "conceptTested": "e.g. Authentication, Database Design, Error Handling",
                    "difficulty": "%s"
                  }
                ]
                """.formatted(repoName,repoSummary,difficulty,difficultyInstructions,difficulty);
    }
    private List<ParsedQuestion> parsedQuestionsFromResponse(String rawResponse){
        try{
            // Clean up common LLM formatting issues
            // LLMs sometimes add ```json ... ``` even when told not to
            String cleaned = rawResponse
                    .replaceAll("(?s)```json\\s*", "")  // remove ```json
                    .replaceAll("(?s)```\\s*", "")       // remove closing ```
                    .trim();

            // Find the JSON array boundaries defensively
            // Some models add text before or after the JSON
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');

            if (start == -1 || end == -1) {
                log.error("No JSON array found in LLM response: {}", cleaned);
                throw new RuntimeException("LLM did not return valid JSON");
            }

            String jsonOnly = cleaned.substring(start, end + 1);
            return objectMapper.readValue(
                    jsonOnly,
                    new TypeReference<List<ParsedQuestion>>() {}
            );
        }catch (Exception e) {
            log.error("Failed to parse interview questions from LLM response: {}", e.getMessage());
            throw new RuntimeException("Failed to generate questions. Please try again.");
        }
    }
    private InterviewSessionDto toDto(
            InterviewSession session,
            List<InterviewQuestion> questions,
            RepoEntity repo) {

        List<InterviewQuestionDto> questionDtos = questions.stream()
                .map(q -> InterviewQuestionDto.builder()
                        .id(q.getId())
                        .question(q.getQuestion())
                        .expectedAnswer(q.getExpectedAnswer())
                        .difficulty(q.getDifficulty())
                        .conceptTested(q.getConceptTested())
                        .questionOrder(q.getQuestionOrder())
                        .build())
                .toList();

        return InterviewSessionDto.builder()
                .id(session.getId())
                .repoId(repo.getId())
                .repoName(repo.getRepoName())
                .difficulty(session.getDifficulty())
                .questions(questionDtos)
                .createdAt(session.getCreatedAt())
                .build();
    }

    // Internal record for parsing LLM JSON response
    // Jackson maps the JSON fields to these record components
    private record ParsedQuestion(
            String question,
            String expectedAnswer,
            String conceptTested,
            String difficulty
    ) {}
}
