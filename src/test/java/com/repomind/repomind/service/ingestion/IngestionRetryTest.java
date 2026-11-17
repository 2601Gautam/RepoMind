package com.repomind.repomind.service.ingestion;

import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.S3StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionRetryTest {
    @Test void transientEmbeddingFailureRetriesLocallyWithoutReleasingJob() throws Exception {
        var clone = mock(FileCloneService.class);
        var embeddings = mock(EmbeddingService.class);
        var store = mock(FullIngestionStore.class);
        var heartbeat = mock(IngestionHeartbeat.class);
        var renewal = mock(AutoCloseable.class);
        var repo = RepoEntity.builder().id(UUID.randomUUID()).ingestionOwner(UUID.randomUUID())
                .status(RepoEntity.IngestionStatus.PROCESSING).build();
        var policy = new EmbeddingRetryPolicy(2,1,5,1000,1,1000,ms -> {},System::nanoTime,() -> 0.5);
        var service = new IngestionService(clone,new ChunkingService(),embeddings,policy,store,heartbeat,
                mock(CacheService.class),mock(S3StorageService.class));
        ReflectionTestUtils.setField(service,"embeddingBatchSize",1);
        ReflectionTestUtils.setField(service,"ingestionLeaseSeconds",60L);
        when(store.claim(repo.getId(),60)).thenReturn(Optional.of(repo));
        when(heartbeat.start(repo.getId(),repo.getIngestionOwner(),60)).thenReturn(renewal);
        when(clone.cloneRepository("fixture",null)).thenReturn(Path.of("fixture"));
        when(clone.extractFiles(Path.of("fixture"))).thenReturn(List.of(
                new FileCloneService.ParsedFile("One.java","class One {}",".java")));
        when(embeddings.embedBatch(anyList())).thenThrow(new TransientAiException("temporary provider failure"))
                .thenReturn(List.of(new float[1024]));

        service.processIngestionJob(repo.getId(),"fixture",null);

        assertEquals(RepoEntity.IngestionStatus.READY,repo.getStatus());
        verify(embeddings,times(2)).embedBatch(anyList());
        verify(embeddings,never()).embed(anyString());
        verify(store,times(1)).appendBatch(eq(repo.getId()),eq(repo.getIngestionOwner()),argThat(batch -> batch.size()==1));
        verify(store,never()).releaseForRetry(any(),any(),any());
        verify(renewal).close();
    }
}
