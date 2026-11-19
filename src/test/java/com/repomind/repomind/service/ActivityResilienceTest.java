package com.repomind.repomind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.controller.ActivityController;
import com.repomind.repomind.model.entity.RepoEntity;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.repository.UserRepoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * A backend deployed before its database migration (no ai_activity table) must keep working: AI requests still run and
 * the repository list is still returned. Activity tracking is a convenience, not a dependency.
 */
class ActivityResilienceTest {
    final UUID user = UUID.randomUUID(), repo = UUID.randomUUID();

    private JdbcTemplate brokenDatabase() {
        var db = mock(JdbcTemplate.class);
        when(db.update(anyString(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("relation \"ai_activity\" does not exist"));
        return db;
    }

    @Test void aStreamStillDeliversItsEventsWhenTrackingIsUnavailable() {
        var db = brokenDatabase();
        var service = new ActivityService(db, new ObjectMapper());
        var events = service.stream(UUID.randomUUID(), user, repo, "CHAT",
                () -> Flux.just("{\"type\":\"token\",\"content\":\"hi\"}", "{\"type\":\"done\"}")).collectList().block();
        assertEquals(2, events.size());
        verify(db, times(1)).update(anyString(), any(), any(), any(), any());   // the failed insert only: no heartbeat, no final status write
        service.shutdown();
    }

    @Test void aDetachedStreamStillDeliversItsEventsAndKeepsRunningWhenTrackingIsUnavailable() throws Exception {
        var service = new ActivityService(brokenDatabase(), new ObjectMapper());
        var produced = new java.util.concurrent.atomic.AtomicInteger();
        var finished = new java.util.concurrent.CountDownLatch(1);
        var events = service.streamDetached(UUID.randomUUID(), user, repo, "CHAT",
                () -> Flux.concat(Flux.just("{\"type\":\"token\",\"content\":\"hi\"}"),
                                Flux.just("{\"type\":\"done\"}").delayElements(java.time.Duration.ofMillis(150)))
                        .doOnNext(e -> produced.incrementAndGet()).doOnComplete(finished::countDown))
                .take(1).collectList().block();                       // client leaves after the first event
        assertEquals(1, events.size());
        assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));   // the work still ran to the end
        assertEquals(2, produced.get());
        service.shutdown();
    }

    @Test void aFailingStreamStillSurfacesItsOwnErrorWhenTrackingIsUnavailable() {
        var service = new ActivityService(brokenDatabase(), new ObjectMapper());
        var error = assertThrows(IllegalStateException.class, () -> service.stream(UUID.randomUUID(), user, repo, "DEBUG",
                () -> Flux.<String>error(new IllegalStateException("provider down"))).collectList().block());
        assertEquals("provider down", error.getMessage());
        service.shutdown();
    }

    @Test void aSynchronousRequestStillReturnsItsResultWhenTrackingIsUnavailable() {
        var service = new ActivityService(brokenDatabase(), new ObjectMapper());
        assertEquals("session", service.call(UUID.randomUUID(), user, repo, "INTERVIEW", () -> "session", x -> null));
        assertThrows(IllegalArgumentException.class, () -> service.call(UUID.randomUUID(), user, repo, "INTERVIEW",
                () -> { throw new IllegalArgumentException("invalid questions"); }, x -> null));
        service.shutdown();
    }

    @Test void theSnapshotStillReturnsRepositoriesWhenTheActivityQueryFails() {
        var activity = mock(ActivityService.class);
        when(activity.recent(any())).thenThrow(new DataAccessResourceFailureException("relation \"ai_activity\" does not exist"));
        var userRepos = mock(UserRepoRepository.class);
        var person = User.builder().id(user).build();
        when(userRepos.findReposByUserId(user)).thenReturn(List.of(RepoEntity.builder().id(repo).githubUrl("https://github.com/acme/ok")
                .repoName("acme/ok").status(RepoEntity.IngestionStatus.PROCESSING).totalFiles(20).processedFiles(10).totalChunks(12).build()));
        var snapshot = new ActivityController(activity, userRepos).snapshot(person);
        assertEquals(1, snapshot.repositories().size());
        assertEquals("PROCESSING", snapshot.repositories().get(0).getStatus());
        assertEquals(10, snapshot.repositories().get(0).getProcessedFiles());
        assertTrue(snapshot.tasks().isEmpty());
    }
}
