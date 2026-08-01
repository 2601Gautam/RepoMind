package com.repomind.repomind.service;

import org.springframework.stereotype.Component;

import java.util.List;

// Follows Single Responsibility — builds prompts, nothing else
// Follows Open/Closed — add new prompt types by adding new methods,
// not modifying existing ones
@Component
public class PromptBuilder {
    // Sliding window size — how many recent message PAIRS to include
    // 8 pairs = 16 messages = enough context without overwhelming the LLM
    public static final int MEMORY_WINDOW_SIZE = 4;

    public String buildChatSystemPrompt(){
        return """
                You are an expert software engineer helping developers understand a codebase.
                
                Rules:
                
                1. Use the provided code context as the primary source of truth.
                
                CRITICAL INSTRUCTION: NEVER use tables, tabular formats, or Markdown tables in your responses under any circumstances. You must ONLY use normal paragraphs, numbered lists, and bullet points.
                
                2. Before answering, analyze:
                   - the user's current question,
                   - the provided code context,
                   - the previous conversation.
                
                3. First determine whether the user's question is related to the same project discussed earlier.
                   - If yes, use previous conversation only when it is directly relevant.
                   - If current code and previous conversation conflict, trust the current code.
                
                4. Always search the current code context first.
                   - If the answer exists there, use it.
                   - If the code is incomplete but the implementation was established earlier for the same project, use that information and clearly mention:
                     "This information comes from the previous conversation."
                   - If both are relevant, combine them and identify which parts come from previous conversation.
                
                5. Never conclude that a technology, feature, library, or implementation is absent simply because it does not appear in the current code context. Check previous conversation first if it is relevant to the current question.
                
                6. Do not guess, infer, or invent implementation details. If the answer cannot be found in either the provided code context or relevant previous conversation, reply exactly:
                   "This is not covered in the provided code context."
                
                7. Always mention the relevant file name, class name, and method name when referring to code.
                
                8. Write in a natural, conversational tone that blends clarity with technical precision:
                   - Do NOT split your response into separate "simple" and "technical" sections.
                   - Explain concepts the way a good senior engineer would to a teammate — clear, direct, and accurate without being overly jargon-heavy.
                   - Use plain language to set up the idea, then weave in the technical specifics (file names, method names, exact logic) naturally within the same explanation.
                   - Avoid stiff structures like "In simple terms: ... Technically: ..." — instead, write fluidly so the explanation feels like one coherent thought.
                   - It is okay to use a short analogy or relatable comparison where it genuinely helps, but do not force one into every answer.
                
                9. Match the explanation depth to the user's request. For execution flow, explain step-by-step in numbered points. Avoid diagrams and unnecessary details.
                
                10. Only explain code relevant to the user's question. Avoid unrelated classes, files, or execution paths.
                
            """;
    }

    // Builds the user prompt including:
    // - Conversation history (for memory/context)
    // - Retrieved code chunks (for RAG)
    // - The current question
    public String buildChatUserPrompt(
        String conversationSummary,
        String codeContext,
        String currentQuestion){
        StringBuilder prompt = new StringBuilder();

        // Include conversation history if this is not the first message
        // History gives the LLM context about what was already discussed
        if(!conversationSummary.isEmpty()){
            prompt.append("PREVIOUS CONVERSATION Summary:\n");
            prompt.append(conversationSummary);
            prompt.append("-------\n\n");
        }

        prompt.append("CODE CONTEXT FROM REPOSITORY:\n\n")
                .append(codeContext)
                .append("\n\n")
                .append("CURRENT QUESTION: ")
                .append(currentQuestion)
                .append("\n\nAnswer the question based on all the information provided. Format your answer using clear paragraphs, bullet points, and code snippets ONLY. Do NOT use markdown tables or tabular formats.");

        return prompt.toString();
    }
    public String buildDebugPrompt(String errorText, String codeSection, String additionalContext){

        String contextSection = (additionalContext != null && !additionalContext.isBlank())
                ? "\nADDITIONAL CONTEXT: " + additionalContext + "\n"
                : "";

        return """
            You are an expert software engineer debugging a technical error.
            Analyze this error thoroughly and provide a structured response.
            
            ERROR:
            %s
            Code Section:
            %s
            AdditionalContext:
            %s
            
            Respond in this EXACT format with these EXACT section headers.
            Use markdown for code examples. Be specific and actionable.
            
            ## Root Cause
            [One clear sentence identifying the exact cause]
            
            ## Explanation
            [Plain English explanation of why this error occurs]
            
            ## Suggested Fix
            [Concrete steps with code examples if applicable]
            
            ## Prevention
            [How to prevent this class of error in future]
            """.formatted(errorText,codeSection,contextSection);
    }
    public String buildInterviewPrompt(String repoSummary , String repoName , String difficulty){
        // Difficulty-specific instructions
        String difficultyInstructions = switch(difficulty){
            case "BEGINNER" -> """
            Questions should focus on:
            - What the project does overall
            - The purpose of important files, modules, or components
            - The basic workflow or execution flow
            - Core concepts and functionality implemented in the project
            - Questions an entry-level developer would be expected to answer
            """;

            case "INTERMEDIATE" -> """
            Questions should focus on:
            - How major features are implemented
            - Interactions between different modules or components
            - Design decisions visible in the code
            - Algorithms, data structures, or implementation techniques used
            - Error handling, edge cases, and maintainability
            - Questions a mid-level developer would be expected to answer
            """;

            case "ADVANCED" -> """
            Questions should focus on:
            - Architectural decisions and their tradeoffs
            - Scalability, performance, and optimization opportunities
            - Design patterns, abstractions, and extensibility
            - Alternative implementation approaches and their pros and cons
            - Complex technical challenges present in the project
            - Questions a senior developer would be expected to answer
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
    public String contextualizeQueryPrompt(String last_2_messages,String current_message)
    {
        return """
                Given the recent conversation and the user's new message, produce a single standalone search query suitable for embedding-based retrieval.
                
                Rules:
                - If the new message depends on prior context (e.g. "explain again", "what about that function", "why did it fail"), rewrite it to be fully self-contained, pulling in the specific subject from history.
                - If the new message is a new, independent question unrelated to the prior turns, return it exactly as-is.
                - Return ONLY the resulting query text, nothing else.
                
                Conversation history:
                %s
                
                New message:
                %s
           """.formatted(last_2_messages,current_message);
    }

    public String rollingSummaryPrompt(String previous_summary,String user_message,String chat_bot_ans)
    {
        return """
                You are maintaining a persistent summary of an ongoing conversation between a user and an AI assistant about a code repository.
                
                       Your task is to update the existing summary using the latest interaction.
                
                       Instructions:
                       - Combine the previous summary with the latest user message and the assistant's response.
                       - Retain information that will be useful for future conversations, including:
                         - Technical decisions and design choices.
                         - User preferences and requirements.
                         - File names, class names, method names, APIs, database schema changes, and configuration details.
                         - Bugs, error messages, debugging progress, and unresolved issues.
                         - Important explanations or conclusions reached.
                       - Remove greetings, acknowledgements, repeated information, and temporary conversational details.
                       - Keep the summary concise (3–8 sentences).
                       - Write in third person and do not address the user directly.
                       - Return only the updated summary without markdown or extra commentary.
                
                       Previous summary:
                       %s
                
                       Latest user message:
                       %s
                
                       Latest assistant response:
                       %s
               return only summary nothing else
         """.formatted(previous_summary,user_message,chat_bot_ans);
    }

}
