package com.repomind.repomind.evaluation;

import com.repomind.repomind.service.ActivityService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What the browser sees after a network drop, restart or fresh login: the server-side record of each AI request.
 * Real PostgreSQL (disposable eval database); the model call is a controlled Flux.
 */
@SpringBootTest @ActiveProfiles("eval")
class ActivityRecoveryIT {
    @Autowired JdbcTemplate sql;
    @Autowired ActivityService activity;
    UUID user, other, repo;

    @BeforeEach void seed() {
        assertEquals("repomind_eval", sql.queryForObject("select current_database()", String.class));
        user = UUID.randomUUID(); other = UUID.randomUUID(); repo = UUID.randomUUID();
        for (UUID u : List.of(user, other))
            sql.update("insert into users(id,email,password_hash) values(?,?, 'x')", u, u + "@activity.invalid");
        sql.update("insert into repositories(id,github_url,status) values(?,?, 'READY')", repo, "https://github.com/fixture/" + repo);
        sql.update("insert into user_repos(user_id,repo_id) values(?,?)", user, repo);
    }
    @AfterEach void cleanup() {
        sql.update("delete from users where id in (?,?)", user, other);   // cascades to ai_activity and user_repos
        sql.update("delete from repositories where id=?", repo);
    }
    String statusOf(UUID id) { return sql.queryForObject("select status from ai_activity where id=?", String.class, id); }
    ActivityService.Activity only() {
        var rows = activity.recent(user);
        assertEquals(1, rows.size());
        return rows.get(0);
    }

    @Test void completedStreamIsRecoverableWithItsConversation() {
        UUID id = UUID.randomUUID(), conversation = UUID.randomUUID();
        var out = activity.stream(id, user, repo, "CHAT", () -> Flux.just(
                "{\"type\":\"token\",\"content\":\"hi\"}",
                "{\"type\":\"done\",\"conversationId\":\"" + conversation + "\"}")).collectList().block();
        assertEquals(2, out.size());
        var row = only();
        assertEquals("COMPLETED", row.status());
        assertEquals(conversation, row.resultId());
        assertEquals("CHAT", row.kind());
    }

    @Test void providerErrorEventIsFailedNotCompleted() {
        UUID id = UUID.randomUUID();
        activity.stream(id, user, repo, "CHAT", () -> Flux.just("{\"type\":\"error\",\"message\":\"boom\"}")).collectList().block();
        assertEquals("FAILED", statusOf(id));
    }

    @Test void exceptionFromTheModelIsFailed() {
        UUID id = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> activity.stream(id, user, repo, "DEBUG",
                () -> Flux.<String>error(new IllegalStateException("provider down"))).collectList().block());
        assertEquals("FAILED", statusOf(id));
    }

    @Test void clientDisconnectIsInterruptedAndNeverCompleted() {
        UUID id = UUID.randomUUID();
        activity.stream(id, user, repo, "CHAT", () -> Flux.interval(Duration.ofMillis(20)).map(i -> "{\"type\":\"token\",\"content\":\"x\"}"))
                .take(2).collectList().block();                       // take() cancels upstream, like a dropped connection
        assertEquals("INTERRUPTED", statusOf(id));
    }

    String awaitStatus(UUID id, String wanted) throws InterruptedException {
        String status = null;
        for (int i = 0; i < 100; i++) {
            status = statusOf(id);
            if (wanted.equals(status)) return status;
            Thread.sleep(50);
        }
        return status;
    }
    String token() { return "{\"type\":\"token\",\"content\":\"x\"}"; }

    // ---- chat is detached from the browser connection: a page reload or closed tab must not lose the answer
    @Test void detachedChatKeepsGeneratingAfterTheClientLeavesAndEndsCompleted() throws Exception {
        UUID id = UUID.randomUUID(), conversation = UUID.randomUUID();
        var produced = new java.util.concurrent.atomic.AtomicInteger();
        var received = new ArrayList<String>();
        activity.streamDetached(id, user, repo, "CHAT", () -> Flux.concat(
                        Flux.interval(Duration.ofMillis(40)).take(6).map(i -> token()),
                        Flux.just("{\"type\":\"done\",\"conversationId\":\"" + conversation + "\"}"))
                        .doOnNext(e -> produced.incrementAndGet()))
                .take(2).doOnNext(received::add).collectList().block();        // the client goes away after two events
        assertEquals(2, received.size());
        assertEquals("COMPLETED", awaitStatus(id, "COMPLETED"));                 // not INTERRUPTED
        assertEquals(7, produced.get());                                         // the model ran to the end, so the answer could be saved
        assertEquals(conversation, only().resultId());
    }

    @Test void detachedChatDeliversEveryEventToAClientThatStaysConnected() {
        UUID id = UUID.randomUUID(), conversation = UUID.randomUUID();
        var out = activity.streamDetached(id, user, repo, "CHAT", () -> Flux.just(token(), token(),
                "{\"type\":\"done\",\"conversationId\":\"" + conversation + "\"}")).collectList().block();
        assertEquals(3, out.size());
        assertEquals("COMPLETED", statusOf(id));
    }

    @Test void detachedChatWithoutAFinalEventIsStillInterruptedNotCompleted() {
        UUID id = UUID.randomUUID();
        activity.streamDetached(id, user, repo, "CHAT", () -> Flux.just(token())).collectList().block();
        assertEquals("INTERRUPTED", statusOf(id));
    }

    @Test void detachedProviderFailureReachesTheClientAndIsFailed() {
        UUID error = UUID.randomUUID(), event = UUID.randomUUID();
        var thrown = assertThrows(IllegalStateException.class, () -> activity.streamDetached(error, user, repo, "CHAT",
                () -> Flux.<String>error(new IllegalStateException("provider down"))).collectList().block());
        assertEquals("provider down", thrown.getMessage());
        assertEquals("FAILED", statusOf(error));
        activity.streamDetached(event, user, repo, "CHAT", () -> Flux.just("{\"type\":\"error\",\"content\":\"boom\"}")).collectList().block();
        assertEquals("FAILED", statusOf(event));
    }

    @Test void detachedRequestIdReuseIsStillRejectedWithoutRunningTwice() {
        UUID id = UUID.randomUUID();
        int[] calls = {0};
        activity.streamDetached(id, user, repo, "CHAT", () -> { calls[0]++; return Flux.just("{\"type\":\"done\"}"); }).collectList().block();
        var error = assertThrows(ResponseStatusException.class, () ->
                activity.streamDetached(id, user, repo, "CHAT", () -> { calls[0]++; return Flux.just("{\"type\":\"done\"}"); }).collectList().block());
        assertEquals(409, error.getStatusCode().value());
        assertEquals(1, calls[0]);
    }

    @Test void detachedProviderThatGoesSilentIsFailedInsteadOfRunningForever() throws Exception {
        UUID id = UUID.randomUUID();
        activity.silenceLimit(Duration.ofMillis(300));
        try {
            var error = assertThrows(RuntimeException.class, () -> activity.streamDetached(id, user, repo, "CHAT",
                    () -> Flux.concat(Flux.just(token()), Flux.<String>never())).collectList().block(Duration.ofSeconds(10)));
            assertInstanceOf(java.util.concurrent.TimeoutException.class, error.getCause() == null ? error : error.getCause());
            assertEquals("FAILED", awaitStatus(id, "FAILED"));
        } finally { activity.silenceLimit(Duration.ofMinutes(2)); }
    }

    @Test void streamEndingWithoutDoneIsInterrupted() {
        UUID id = UUID.randomUUID();
        activity.stream(id, user, repo, "CHAT", () -> Flux.just("{\"type\":\"token\",\"content\":\"partial\"}")).collectList().block();
        assertEquals("INTERRUPTED", statusOf(id));
    }

    @Test void malformedOutputCannotBecomeCompleted() {
        UUID id = UUID.randomUUID();
        activity.stream(id, user, repo, "CHAT", () -> Flux.just("not json")).collectList().block();
        assertEquals("INTERRUPTED", statusOf(id));
    }

    @Test void resubmittingTheSameRequestIdIsRejectedInsteadOfRunningTwice() {
        UUID id = UUID.randomUUID();
        int[] calls = {0};
        activity.stream(id, user, repo, "CHAT", () -> { calls[0]++; return Flux.just("{\"type\":\"done\"}"); }).collectList().block();
        var error = assertThrows(ResponseStatusException.class, () ->
                activity.stream(id, user, repo, "CHAT", () -> { calls[0]++; return Flux.just("{\"type\":\"done\"}"); }).collectList().block());
        assertEquals(409, error.getStatusCode().value());
        assertEquals(1, calls[0]);
    }

    @Test void interviewCallRecordsResultAndFailureOnTheSameTable() {
        UUID good = UUID.randomUUID(), bad = UUID.randomUUID(), session = UUID.randomUUID();
        assertEquals("ok", activity.call(good, user, repo, "INTERVIEW", () -> "ok", x -> session));
        assertThrows(IllegalStateException.class, () -> activity.call(bad, user, repo, "INTERVIEW",
                () -> { throw new IllegalStateException("invalid questions"); }, x -> null));
        assertEquals("COMPLETED", statusOf(good));
        assertEquals(session, sql.queryForObject("select result_id from ai_activity where id=?", UUID.class, good));
        assertEquals("FAILED", statusOf(bad));
    }

    @Test void runningRequestWithNoHeartbeatIsReportedUnknownNotRunning() {
        UUID fresh = UUID.randomUUID(), dead = UUID.randomUUID();
        sql.update("insert into ai_activity(id,user_id,repo_id,kind,status) values(?,?,?, 'CHAT','RUNNING')", fresh, user, repo);
        sql.update("insert into ai_activity(id,user_id,repo_id,kind,status,touched_at) values(?,?,?, 'DEBUG','RUNNING', current_timestamp - interval '5 minutes')", dead, user, repo);
        var byId = new HashMap<UUID, String>();
        activity.recent(user).forEach(a -> byId.put(a.id(), a.status()));
        assertEquals("RUNNING", byId.get(fresh));
        assertEquals("UNKNOWN", byId.get(dead));   // JVM or network died mid-request: the browser must not show a spinner forever
    }

    @Test void runningRequestKeepsItsHeartbeatWhileTheModelIsSlow() throws Exception {
        UUID id = UUID.randomUUID();
        var visibleWhileSlow = new String[1];
        activity.stream(id, user, repo, "CHAT", () -> Flux.concat(
                Flux.just("{\"type\":\"token\",\"content\":\"a\"}").doOnSubscribe(s ->
                        // make the row look stale right away; only the 10 s heartbeat can bring it back
                        sql.update("update ai_activity set touched_at = current_timestamp - interval '30 seconds' where id=?", id)),
                Flux.just("{\"type\":\"done\"}").delayElements(Duration.ofSeconds(12))))
                .doOnNext(e -> { if (e.contains("done"))
                        visibleWhileSlow[0] = activity.recent(user).get(0).status(); })
                .collectList().block();
        assertEquals("RUNNING", visibleWhileSlow[0]);   // renewed by the heartbeat, so a reload mid-answer shows it as in progress
        assertEquals("COMPLETED", statusOf(id));
    }

    @Test void onlyTheOwnerSeesTheirActivityAndRemovedRepositoriesDropOut() {
        UUID mine = UUID.randomUUID(), theirs = UUID.randomUUID();
        activity.stream(mine, user, repo, "CHAT", () -> Flux.just("{\"type\":\"done\"}")).collectList().block();
        activity.stream(theirs, other, repo, "CHAT", () -> Flux.just("{\"type\":\"done\"}")).collectList().block();
        assertEquals(List.of(mine), activity.recent(user).stream().map(ActivityService.Activity::id).toList());
        sql.update("delete from user_repos where user_id=? and repo_id=?", user, repo);   // user removed the repository
        assertEquals(List.of(), activity.recent(user));
    }

    @Test void activityOlderThanADayIsNotReturnedAndOldMetadataIsPurged() {
        UUID old = UUID.randomUUID();
        sql.update("insert into ai_activity(id,user_id,repo_id,kind,status,started_at,touched_at) values(?,?,?, 'CHAT','COMPLETED', current_timestamp - interval '8 days', current_timestamp - interval '8 days')", old, user, repo);
        assertEquals(List.of(), activity.recent(user));
        activity.purgeOldMetadata();
        assertEquals(0, sql.queryForObject("select count(*) from ai_activity where id=?", Integer.class, old));
    }
}
