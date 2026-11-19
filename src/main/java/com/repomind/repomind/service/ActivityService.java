package com.repomind.repomind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.SignalType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

@Service @RequiredArgsConstructor @Slf4j
public class ActivityService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final ScheduledExecutorService heartbeat = Executors.newScheduledThreadPool(2);
    private volatile Duration silenceLimit = Duration.ofMinutes(2);

    public record Activity(UUID id, UUID repoId, String kind, String status, UUID resultId,
                           OffsetDateTime startedAt, OffsetDateTime updatedAt) {}

    private UUID begin(UUID requestedId, UUID user, UUID repo, String kind) {
        UUID id = requestedId == null ? UUID.randomUUID() : requestedId;
        int inserted;
        try {
            inserted = db.update("insert into ai_activity(id,user_id,repo_id,kind,status) values(?,?,?,?,'RUNNING') on conflict(id) do nothing",
                    id,user,repo,kind);
        } catch (DataAccessException e) {
            // Status tracking is a convenience. A missing table (migration not applied yet) or a database hiccup must not
            // fail the user's chat, debug or interview request, so continue without tracking it.
            log.warn("Activity tracking unavailable, continuing without it: {}",e.getMessage());
            return null;
        }
        if (inserted == 0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This request was already accepted. Check its activity status.");
        return id;
    }

    private ScheduledFuture<?> renew(UUID id) {
        return heartbeat.scheduleAtFixedRate(() -> {
            try { db.update("update ai_activity set touched_at=current_timestamp where id=? and status='RUNNING'",id); }
            catch (RuntimeException e) { log.warn("Could not renew activity status for {}",id); }
        },10,10,TimeUnit.SECONDS);
    }

    private void finish(UUID id, String status, UUID result) {
        try { db.update("update ai_activity set status=?, result_id=?, touched_at=current_timestamp where id=? and status='RUNNING'",
                status,result,id); }
        catch (RuntimeException e) { log.warn("Could not record final activity status for {}",id); }
    }

    /** The request is cancelled when the client goes away: the record ends INTERRUPTED and the work stops. */
    public Flux<String> stream(UUID request, UUID user, UUID repo, String kind, Supplier<Flux<String>> work) {
        return tracked(request,user,repo,kind,work,null);
    }

    /**
     * Same record keeping, but the work is not tied to the client's connection. A reloaded page, a closed tab or a dropped
     * connection only stops the delivery: the model keeps generating, the caller's own completion step (saving the answer)
     * still runs, and the record ends COMPLETED, so the answer is waiting in the saved history when the user returns.
     * A provider that goes silent for longer than the silence limit is failed rather than left running with nobody able to cancel it.
     */
    public Flux<String> streamDetached(UUID request, UUID user, UUID repo, String kind, Supplier<Flux<String>> work) {
        return Flux.defer(() -> {
            Sinks.Many<String> relay = Sinks.many().replay().all();
            tracked(request,user,repo,kind,work,silenceLimit).subscribe(
                    relay::tryEmitNext, relay::tryEmitError, relay::tryEmitComplete);
            return relay.asFlux();
        });
    }

    /** Test hook: shortens the silence limit of {@link #streamDetached}. */
    public ActivityService silenceLimit(Duration limit) { this.silenceLimit = limit; return this; }

    private Flux<String> tracked(UUID request, UUID user, UUID repo, String kind, Supplier<Flux<String>> work, Duration silence) {
        return Flux.defer(() -> {
            UUID id = begin(request,user,repo,kind);
            var renewal = id == null ? null : renew(id);
            var done = new AtomicBoolean();
            var failed = new AtomicBoolean();
            var result = new AtomicReference<UUID>();
            Flux<String> source = Flux.defer(work);
            return (silence == null ? source : source.timeout(silence))
                    .doOnNext(event -> {
                        try {
                            var node = json.readTree(event);
                            if ("done".equals(node.path("type").asText())) done.set(true);
                            if ("error".equals(node.path("type").asText())) failed.set(true);
                            if (node.hasNonNull("conversationId")) result.set(UUID.fromString(node.get("conversationId").asText()));
                        } catch (Exception ignored) { /* malformed output must not become COMPLETED */ }
                    })
                    .doOnError(error -> failed.set(true))
                    .doFinally(signal -> {
                        if (renewal != null) renewal.cancel(false);
                        if (id == null) return;
                        String state = failed.get() ? "FAILED" :
                                signal == SignalType.CANCEL ? "INTERRUPTED" :
                                done.get() ? "COMPLETED" : "INTERRUPTED";
                        finish(id,state,result.get());
                    });
        });
    }

    public <T> T call(UUID request, UUID user, UUID repo, String kind, Supplier<T> work, Function<T,UUID> resultId) {
        UUID id = begin(request,user,repo,kind);
        var renewal = id == null ? null : renew(id);
        try {
            T result = work.get();
            if (id != null) finish(id,"COMPLETED",resultId.apply(result));
            return result;
        } catch (RuntimeException e) {
            if (id != null) finish(id,"FAILED",null);
            throw e;
        } finally { if (renewal != null) renewal.cancel(false); }
    }

    public List<Activity> recent(UUID user) {
        return db.query("""
                select id,repo_id,kind,
                       case when status='RUNNING' and touched_at < current_timestamp-interval '60 seconds'
                            then 'UNKNOWN' else status end as visible_status,
                       result_id,started_at,touched_at
                  from ai_activity a
                 where user_id=? and started_at > current_timestamp-interval '24 hours'
                   and (repo_id is null or exists(select 1 from user_repos ur where ur.user_id=a.user_id and ur.repo_id=a.repo_id))
                 order by started_at desc limit 100
                """,(rs,n)->new Activity(rs.getObject("id",UUID.class),rs.getObject("repo_id",UUID.class),
                        rs.getString("kind"),rs.getString("visible_status"),rs.getObject("result_id",UUID.class),
                        rs.getObject("started_at",OffsetDateTime.class),rs.getObject("touched_at",OffsetDateTime.class)),user);
    }

    @Scheduled(fixedDelay = 3600000)
    public void purgeOldMetadata() {
        db.update("delete from ai_activity where touched_at < current_timestamp-interval '7 days'");
    }
    @PreDestroy void shutdown() { heartbeat.shutdownNow(); }
}

