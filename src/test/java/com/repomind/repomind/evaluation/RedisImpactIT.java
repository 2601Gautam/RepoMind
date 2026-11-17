package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.controller.IngestionController;
import com.repomind.repomind.dto.response.RepoStatusResponse;
import com.repomind.repomind.model.entity.User;
import com.repomind.repomind.service.CacheService;
import com.repomind.repomind.service.ConversationMemoryService.MemoryMessage;
import com.repomind.repomind.service.PromptBuilder;
import com.repomind.repomind.service.RedisConversationMemoryService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.cache.type=redis", "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session.events.log=false", "logging.level.org.hibernate.orm.metrics=OFF"})
@ActiveProfiles("eval")
class RedisImpactIT {
    private static final int USERS = 10;
    private static final int REPOS = 20;
    private static final int HISTORY = 500;
    private static final int ROUNDS = 3;
    private static final int READS = 200;
    private static final int WINDOW = PromptBuilder.MEMORY_WINDOW_SIZE;
    private static final String RECENT = """
            SELECT role, content FROM messages WHERE conversation_id = ?
            ORDER BY created_at DESC, id DESC LIMIT ?
            """;
    @Autowired IngestionController cached;
    @Autowired CacheService invalidation;
    @Autowired CacheManager cacheManager;
    @Autowired RedisConversationMemoryService memory;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate sql;
    @Autowired EntityManagerFactory emf;

    record Subject(User user, UUID conversation, List<MemoryMessage> window, List<RepoStatusResponse> repos) {}
    record Observation(int round, int request, int person, String mode, long microseconds, long listSql) {}

    private List<MemoryMessage> fromPostgres(UUID conversation, int limit) {
        // Match the production Redis result: chronological order, same role and content.
        return sql.query(RECENT, (rs, row) -> new MemoryMessage(rs.getString(1), rs.getString(2)),
                conversation, limit).reversed();
    }

    private <T> void measure(int round, int request, int person, String mode, Supplier<T> read,
                             T expected, Statistics stats, List<Observation> observations) {
        long before = stats.getPrepareStatementCount();
        long started = System.nanoTime();
        T actual = read.get();
        long micros = (System.nanoTime() - started) / 1000;
        long statements = stats.getPrepareStatementCount() - before;
        assertEquals(expected, actual, mode);
        observations.add(new Observation(round, request, person, mode, micros, statements));
    }

    private Subject seed(int n, UUID userId, IngestionController direct) {
        User user = User.builder().id(userId).build();
        UUID conversation = UUID.randomUUID();
        sql.update("insert into users(id,email) values(?,?)", userId, userId + "@example.invalid");
        sql.update("""
                insert into repositories(user_id,github_url,repo_name,status)
                select ?, 'https://example.invalid/' || cast(? as text) || '/' || i, 'owner-' || ? || '/' || i, 'READY'
                from generate_series(1,?) i
                """, userId, userId, n, REPOS);
        sql.update("""
                insert into user_repos(user_id,repo_id,added_at)
                select ?, id, timestamp '2026-01-01' + row_number() over(order by repo_name) * interval '1 second'
                from repositories where user_id=?
                """, userId, userId);
        sql.update("insert into conversations(id,user_id) values(?,?)", conversation, userId);
        sql.update("""
                insert into messages(conversation_id,role,content,created_at)
                select ?, case when i%2=0 then 'assistant' else 'user' end,
                       'owner ' || ? || ' turn ' || i || ' ' || repeat('synthetic context ',16),
                       timestamp '2026-01-01' + i * interval '1 second'
                from generate_series(1,?) i
                """, conversation, n, HISTORY);
        for (MemoryMessage m : fromPostgres(conversation, 12)) memory.addMessage(conversation, m.role(), m.content());
        List<RepoStatusResponse> repos = direct.listAll(user);
        assertEquals(REPOS, repos.size());
        assertTrue(repos.stream().allMatch(r -> r.getRepoName().startsWith("owner-" + n + "/")));
        return new Subject(user, conversation, fromPostgres(conversation, WINDOW), repos);
    }

    @Test void compareRepositoryListsAndConversationWindows() throws Exception {
        assertEquals("repomind_eval", sql.queryForObject("select current_database()", String.class));
        assertInstanceOf(RedisCacheManager.class, cacheManager);
        IngestionController direct = AopTestUtils.getUltimateTargetObject(cached);
        assertNotSame(direct, cached);
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        assertTrue(stats.isStatisticsEnabled());
        List<UUID> owners = new ArrayList<>();
        List<Subject> subjects = new ArrayList<>();
        List<Observation> lists = new ArrayList<>(), windows = new ArrayList<>();
        String index = "redis_eval_window_" + UUID.randomUUID().toString().replace("-", "");
        try {
            for (int i = 0; i < USERS; i++) {
                UUID id = UUID.randomUUID();
                owners.add(id);
                subjects.add(seed(i, id, direct));
            }
            // Evaluation-only comparator, also valid for an installation with an optimized history query.
            sql.execute("create index " + index + " on messages(conversation_id,created_at desc,id desc)");
            sql.execute("analyze messages");
            var plan = sql.queryForList("explain (costs off) " + RECENT, String.class,
                    subjects.getFirst().conversation(), WINDOW);
            assertTrue(plan.stream().anyMatch(line -> line.contains("Index Scan")));
            for (int i = 0; i < 100; i++) {
                Subject s = subjects.get(i % USERS);
                assertEquals(s.repos(), direct.listAll(s.user()));
                assertEquals(s.repos(), cached.listAll(s.user()));
                assertEquals(s.window(), memory.getRecentMessages(s.conversation(), WINDOW));
                assertEquals(s.window(), fromPostgres(s.conversation(), WINDOW));
            }
            for (int round = 0; round < ROUNDS; round++) {
                for (Subject s : subjects) cacheManager.getCache("userRepos").evictIfPresent(s.user().getId());
                for (int i = 0; i < READS; i++) {
                    int person = i % USERS;
                    Subject s = subjects.get(person);
                    for (int j = 0; j < 2; j++) {
                        if ((j + i + round) % 2 == 0) {
                            measure(round, i, person, "uncached", () -> direct.listAll(s.user()), s.repos(), stats, lists);
                        } else {
                            String mode = i < USERS ? "miss" : "hit";
                            measure(round, i, person, mode, () -> cached.listAll(s.user()), s.repos(), stats, lists);
                        }
                    }
                }
                for (int i = 0; i < READS; i++) {
                    int person = i % USERS;
                    Subject s = subjects.get(person);
                    for (int j = 0; j < 2; j++) {
                        if ((j + i + round) % 2 == 0) {
                            measure(round, i, person, "indexedSql", () -> fromPostgres(s.conversation(), WINDOW),
                                    s.window(), stats, windows);
                        } else {
                            measure(round, i, person, "redis", () -> memory.getRecentMessages(s.conversation(), WINDOW),
                                    s.window(), stats, windows);
                        }
                    }
                }
            }
            for (Observation result : lists)
                assertEquals(result.mode().equals("hit") ? 0L : 1L, result.listSql(), result.toString());

            Subject first = subjects.getFirst();
            String listKey = "userRepos::" + first.user().getId();
            long listTtl = redis.getExpire(listKey);
            assertTrue(listTtl > 0 && listTtl <= 300, "list TTL " + listTtl);
            // Change persistent data, verify stale hit and the real worker invalidation.
            UUID repo = first.repos().getFirst().getId();
            assertEquals(1, sql.update("update repositories set repo_name='fresh-name' where id=?", repo));
            assertEquals("fresh-name", direct.listAll(first.user()).getFirst().getRepoName(), "database read");
            assertEquals(first.repos(), cached.listAll(first.user()));
            invalidation.evictUserReposCache();
            assertFalse(redis.hasKey(listKey), "worker invalidation must remove the cached list key");
            assertEquals("fresh-name", cached.listAll(first.user()).getFirst().getRepoName());
            Subject other = subjects.get(1);
            cached.listAll(other.user());
            cached.removeRepo(repo, first.user());
            assertFalse(redis.hasKey(listKey), "member removal must evict the user's cached list on return");
            assertEquals(REPOS - 1, cached.listAll(first.user()).size());
            assertTrue(redis.hasKey("userRepos::" + other.user().getId()));
            assertEquals(other.repos(), cached.listAll(other.user()));
            memory.clearConversation(first.conversation());
            assertTrue(memory.getRecentMessages(first.conversation(), WINDOW).isEmpty());
            assertEquals(first.window(), fromPostgres(first.conversation(), WINDOW));

            var output = new LinkedHashMap<String,Object>();
            output.put("users", USERS); output.put("repositoriesPerUser", REPOS);
            output.put("durableMessagesPerConversation", HISTORY); output.put("window", WINDOW);
            output.put("rounds", ROUNDS); output.put("readsPerPathPerRound", READS);
            output.put("warmupReadsPerPath", 100); output.put("observedRepoListTtlSeconds", listTtl);
            output.put("postgresVersion", sql.queryForObject("show server_version", String.class));
            output.put("redisVersion", redis.execute((org.springframework.data.redis.core.RedisCallback<String>) con ->
                    con.serverCommands().info("server").getProperty("redis_version")));
            output.put("sqlWindowPlan", plan); output.put("repositoryList", lists); output.put("chatWindow", windows);
            new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValue(Path.of("target/redis-impact.json").toFile(), output);
        } finally {
            for (Subject s : subjects) memory.clearConversation(s.conversation());
            for (UUID user : owners) {
                cacheManager.getCache("userRepos").evict(user);
                sql.update("delete from users where id=?", user);
            }
            sql.execute("drop index if exists " + index);
        }
    }
}
