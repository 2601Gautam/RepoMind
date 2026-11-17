package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.repomind.repomind.dto.request.*;
import com.repomind.repomind.model.entity.*;
import com.repomind.repomind.repository.*;
import com.repomind.repomind.service.*;
import com.repomind.repomind.service.ingestion.EmbeddingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiContractTest {
    record Case(String id,String feature,String scenario,String input,String replay,List<String> paths,String expected) {
        @Override public String toString(){return id+" "+feature+" "+scenario;}
    }
    static final ObjectMapper JSON=new ObjectMapper();
    static Stream<Case> cases() throws Exception {
        return JSON.readValue(Path.of("eval/golden/provider-contracts.json").toFile(),new TypeReference<List<Case>>(){}).stream();
    }
    final ChatClient model=mock(ChatClient.class,RETURNS_DEEP_STUBS);
    final EmbeddingService embeddings=mock(EmbeddingService.class);
    final CodeChunkRepository chunks=mock(CodeChunkRepository.class);
    final RepoJpaRepository repos=mock(RepoJpaRepository.class);
    final ConversationRepository conversations=mock(ConversationRepository.class);
    final MessageRepository messages=mock(MessageRepository.class);
    final ConversationMemoryService memory=mock(ConversationMemoryService.class);
    final RepoEntity repo=RepoEntity.builder().id(UUID.randomUUID()).repoName("fixture").status(RepoEntity.IngestionStatus.READY).build();
    final User user=User.builder().id(UUID.randomUUID()).build();
    final Conversation conversation=Conversation.builder().id(UUID.randomUUID()).repository(repo).user(user).build();

    @ParameterizedTest(name="{0}") @MethodSource("cases")
    void featureContract(Case c) throws Exception {
        when(embeddings.embed(anyString())).thenReturn(new float[]{1});
        when(embeddings.embedForQuery(anyString())).thenReturn(new float[]{1});
        when(embeddings.toVectorString(any())).thenReturn("[1]");
        List<CodeChunkRepository.CodeChunkProjection> hits=new ArrayList<>();
        for(String path:c.paths()){
            var chunk=mock(CodeChunkRepository.CodeChunkProjection.class);
            when(chunk.getFilePath()).thenReturn(path);
            when(chunk.getContent()).thenReturn("File: "+path+"\n\nfixture code");
            hits.add(chunk);
        }
        when(chunks.findTopSimilarChunks(eq(repo.getId()),anyString(),anyInt())).thenReturn(hits);
        switch(c.feature()){
            case "chat" -> chat(c);
            case "debug" -> debug(c);
            case "interview" -> interview(c);
            default -> fail(c.feature());
        }
    }

    private void chat(Case c) throws Exception {
        var service=new ChatService(model,embeddings,chunks,conversations,messages,repos,new PromptBuilder(),
                memory,mock(GenerateEmbeddingQuery.class),mock(GenerateSummary.class));
        if(c.scenario().equals("unready")) repo.setStatus(RepoEntity.IngestionStatus.PROCESSING);
        if(c.scenario().equals("wrongRepo")) conversation.setRepository(RepoEntity.builder().id(UUID.randomUUID()).build());
        if(c.scenario().equals("otherUser")) conversation.setUser(User.builder().id(UUID.randomUUID()).build());
        when(repos.findById(repo.getId())).thenReturn(Optional.of(repo));
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        if(Set.of("deny","reject").contains(c.expected())){
            assertThrows(RuntimeException.class,()->service.streamChat(repo.getId(),c.input(),conversation.getId(),user).collectList().block());
            verifyNoInteractions(memory,model,embeddings); return;
        }
        if(!c.expected().equals("empty")) when(model.prompt().system(anyString()).user(anyString()).stream().content()).thenReturn(tokens(c));
        List<String> raw=service.streamChat(repo.getId(),c.input(),conversation.getId(),user).collectList().block();
        if(c.expected().equals("empty")){
            assertStream(c,raw,"No relevant code found for your question.","sources");
            verifyNoInteractions(model);
        } else assertStream(c,raw,c.replay(),"sources");
    }

    private void debug(Case c) throws Exception {
        var request=new DebugRequest(); request.setErrorText(c.input());
        if(c.scenario().equals("repo")) request.setRepoId(repo.getId());
        when(model.prompt().user(anyString()).stream().content()).thenReturn(tokens(c));
        List<String> raw=new DebugService(model,embeddings,chunks,new PromptBuilder()).streamAnalysis(request).collectList().block();
        assertStream(c,raw,c.replay(),"files");
        if(request.getRepoId()==null) verifyNoInteractions(chunks,embeddings);
        else verify(chunks).findTopSimilarChunks(repo.getId(),"[1]",5);
    }

    private Flux<String> tokens(Case c) {
        if(c.scenario().equals("timeout")) return Flux.concat(Flux.just(c.replay()),Flux.error(new IllegalStateException("injected disconnect")));
        int middle=c.replay().length()/2;
        return Flux.just(c.replay().substring(0,middle),c.replay().substring(middle));
    }

    private void assertStream(Case c,List<String> raw,String answer,String pathsField) throws Exception {
        List<JsonNode> events=new ArrayList<>();
        for(String event:raw) events.add(JSON.readTree(event));
        var types=events.stream().map(e->e.path("type").asText()).toList();
        assertEquals("start",types.getFirst());
        assertEquals(1,types.stream().filter(t->t.equals("error")||t.equals("done")).count());
        assertEquals(c.expected().equals("error")?"error":"done",types.getLast());
        assertEquals(answer,events.stream().filter(e->e.path("type").asText().equals("token"))
                .map(e->e.path("content").asText()).collect(java.util.stream.Collectors.joining()));
        if(c.expected().equals("error")) {assertFalse(types.contains("sources")); return;}
        assertEquals("sources",types.get(types.size()-2));
        var paths=events.get(events.size()-2).get(pathsField);
        assertTrue(paths!=null && paths.isArray(),"source paths must round-trip as a JSON array");
        assertEquals(c.paths().stream().distinct().toList(),JSON.convertValue(paths,new TypeReference<List<String>>(){}));
    }

    private void interview(Case c) throws Exception {
        var sessions=mock(InterviewSessionRepository.class);
        var questions=mock(InterviewQuestionRepository.class);
        var access=mock(UserRepoRepository.class);
        when(access.findByUserIdAndRepoId(user.getId(),repo.getId())).thenReturn(Optional.of(UserRepo.builder().user(user).repo(repo).build()));
        when(sessions.save(any())).thenAnswer(i->i.getArgument(0));
        when(questions.save(any())).thenAnswer(i->i.getArgument(0));
        var service=new InterviewService(model,repos,chunks,sessions,questions,JSON,embeddings,access,new PromptBuilder());
        var request=new InterviewRequest(); request.setRepoId(repo.getId()); request.setDifficulty(c.input());
        List<Map<String,String>> items=new ArrayList<>();
        for(int i=0;i<(c.scenario().equals("short")?4:5);i++){
            items.add(Map.of("question",c.scenario().equals("duplicate")?" Same question? ":"Question "+i+"?",
                    "conceptTested","Queues","expectedAnswer",c.scenario().equals("blank")?"  ":"Answer "+i,"difficulty",c.input()));
        }
        String raw=JSON.writeValueAsString(items);
        if(c.scenario().equals("fenced")) raw="Generated:\n" + (char)96+(char)96+(char)96+"json\n"+raw+"\n"+(char)96+(char)96+(char)96;
        if(c.scenario().equals("malformed")) raw="unstructured text";
        when(model.prompt().user(anyString()).call().content()).thenReturn(raw);
        if(c.expected().equals("reject")){
            assertThrows(RuntimeException.class,()->service.generateQuestions(request,user));
            verifyNoInteractions(sessions,questions);
        }else{
            var generated=service.generateQuestions(request,user).getQuestions();
            assertEquals(5,generated.size()); assertEquals("Question 4?",generated.get(4).getQuestion());
            assertEquals(5,generated.get(4).getQuestionOrder());
        }
    }
}

