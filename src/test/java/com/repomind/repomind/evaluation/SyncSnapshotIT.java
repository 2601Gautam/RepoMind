package com.repomind.repomind.evaluation;

import com.repomind.repomind.service.ingestion.*;
import com.repomind.repomind.service.ingestion.GitHubApiService.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("eval")
class SyncSnapshotIT {
    @Autowired JdbcTemplate sql;
    @Autowired SyncService service;
    @MockitoBean GitHubApiService git;
    @MockitoBean EmbeddingService embeddings;
    UUID repo;
    final String url="https://github.com/fixture/sync";
    final RepoCoordinates coords=new RepoCoordinates("fixture","sync");
    final float[] vec=new float[1024];
    @BeforeEach void start(){
        assertEquals("repomind_eval",sql.queryForObject("select current_database()",String.class));
        repo=UUID.randomUUID(); vec[0]=1;
        sql.update("insert into repositories(id,github_url,last_commit_sha,syncing,status,total_chunks) values(?,?, 'old',true,'READY',1)",repo,url+repo);
        sql.update("insert into code_chunks(repo_id,file_path,content) values(?, 'Old.java','old code')",repo);
        when(git.parse(url+repo)).thenReturn(coords);
        when(git.getLatestCommitSha(coords,null)).thenReturn("new");
        when(embeddings.embedBatch(anyList())).thenAnswer(i->Collections.nCopies(((List<?>)i.getArgument(0)).size(),vec));
    }
    @AfterEach void cleanup(){if(repo!=null) sql.update("delete from repositories where id=?",repo);}
    void run(List<ChangedFile> files){
        when(git.compare(coords,"old","new",null)).thenReturn(new CompareResult(files,false,false));
        SyncService target=AopTestUtils.getUltimateTargetObject(service);
        target.syncAsync(repo,null);
        assertEquals(false,sql.queryForObject("select syncing from repositories where id=?",Boolean.class,repo));
    }
    String revision(){return sql.queryForObject("select last_commit_sha from repositories where id=?",String.class,repo);}
    List<String> paths(){return sql.queryForList("select file_path from code_chunks where repo_id=? order by file_path",String.class,repo);}
    @Test void tailSmallerThanBatchCompletesRename(){
        when(git.getBlobContent(coords,"blob",null)).thenReturn("class Renamed {}");
        run(List.of(new ChangedFile("Renamed.java","Old.java","renamed","blob")));
        assertEquals(List.of("Renamed.java"),paths()); assertEquals("new",revision());
        assertTrue(sql.queryForObject("select content from code_chunks where repo_id=?",String.class,repo).contains("class Renamed"));
    }
    @Test void failedReadAfterDeletionKeepsWholeOldSnapshot(){
        when(git.getBlobContent(coords,"blob",null)).thenThrow(new IllegalStateException("fixture unavailable"));
        run(List.of(new ChangedFile("Old.java",null,"removed",null), new ChangedFile("New.java",null,"added","blob")));
        assertEquals("old",revision()); assertEquals(List.of("Old.java"),paths());
    }
    @Test void databaseRejectsInvalidVectorAndRollsBackReplacement(){
        when(git.getBlobContent(coords,"blob",null)).thenReturn("class Bad {}");
        when(embeddings.embedBatch(anyList())).thenReturn(List.of(new float[]{1,2}));
        run(List.of(new ChangedFile("New.java","Old.java","renamed","blob")));
        assertEquals("old",revision()); assertEquals(List.of("Old.java"),paths());
    }
    @Test void deletionAlonePublishesEmptyIndex(){
        run(List.of(new ChangedFile("Old.java",null,"removed",null)));
        assertEquals(List.of(),paths());assertEquals("new",revision());
        verifyNoInteractions(embeddings);
    }
}

