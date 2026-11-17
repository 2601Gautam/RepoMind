package com.repomind.repomind.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repomind.repomind.repository.CodeChunkRepository;
import com.repomind.repomind.repository.CodeChunkRepository.CodeChunkProjection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("eval")
@Transactional
class RetrievalReplayIT {
    private static final int LIMIT = 12;
    private static final int PASSES = 5;
    /** Empty selects the original repository. A set name selects eval/golden/<name>, which is report-only. */
    private static final String SET = System.getProperty("eval.set", "");
    private static final Path GOLDEN = SET.isEmpty() ? Path.of("eval/golden") : Path.of("eval/golden", SET);
    @Autowired JdbcTemplate sql;
    @Autowired CodeChunkRepository chunks;
    private final ObjectMapper json = new ObjectMapper();

    record Span(String path, int start, int end) {}
    record Query(String id, String question, String vector, Span expected) {}
    record Result(String id, Span expected, List<Span> ranked, Integer firstSupportingRank, List<Long> lookupMicros) {}

    private String vector(JsonNode small) {
        assertEquals(384, small.size());
        // Zero padding leaves norms, dot products and cosine distances unchanged.
        List<String> values = new ArrayList<>(1024);
        small.forEach(v -> values.add(v.asText()));
        values.addAll(Collections.nCopies(1024 - values.size(), "0"));
        return "[" + String.join(",", values) + "]";
    }

    @Test void pairedReplay() throws Exception {
        assertEquals("repomind_eval", sql.queryForObject("select current_database()", String.class));
        Path file = GOLDEN.resolve("minilm-replay.json.gz");
        JsonNode fixture;
        try (var in = new GZIPInputStream(Files.newInputStream(file))) { fixture = json.readTree(in); }
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] labels = Files.readString(GOLDEN.resolve("retrieval.tsv")).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        assertEquals(fixture.path("labelsSha256").asText(), HexFormat.of().formatHex(digest.digest(labels)));
        UUID repo = UUID.randomUUID();
        sql.update("insert into repositories(id, github_url, repo_name) values(?,?,?)",repo,"eval:" + repo,"pinned source snapshot");
        List<Object[]> inserts = new ArrayList<>();
        for (JsonNode row : fixture.path("chunks")) {
            String path = row.get("path").asText();
            int start = row.get("start").asInt(), end = row.get("end").asInt();
            UUID id = UUID.nameUUIDFromBytes((path + ":" + start).getBytes(StandardCharsets.UTF_8));
            inserts.add(new Object[]{id, repo, path, start, end, row.get("text").asText(), vector(row.get("embedding"))});
        }
        sql.batchUpdate("insert into code_chunks(id,repo_id,file_path,start_line,end_line,content,embedding) values(?,?,?,?,?,?,cast(? as vector))",inserts);
        sql.execute("set local ivfflat.probes = 100");
        List<Query> queries = new ArrayList<>();
        for (JsonNode row : fixture.path("cases")) {
            var span = new Span(row.get("path").asText(),row.get("start").asInt(),row.get("end").asInt());
            assertTrue(inserts.stream().anyMatch(c -> c[2].equals(span.path()) && (int)c[3] <= span.start() && (int)c[4] >= span.end()), row.toString().substring(0,70));
            if (row.get("id").asText().startsWith("DEV") == Boolean.getBoolean("eval.dev"))
                queries.add(new Query(row.get("id").asText(),row.get("question").asText(),vector(row.get("embedding")),span));
        }
        assertEquals(Boolean.getBoolean("eval.dev") ? 4 : 40, queries.size());
        Map<String,Function<Query,List<CodeChunkProjection>>> modes = new LinkedHashMap<>();
        modes.put("vector", q -> chunks.findTopSimilarChunks(repo,q.vector(),LIMIT));
        modes.put("candidate", q -> com.repomind.repomind.service.ChatRetrieval.search(chunks,repo,q.vector(),q.question(),LIMIT));
        Map<String,List<Result>> results = new LinkedHashMap<>();
        Map<String,Map<String,Result>> byId = new LinkedHashMap<>();
        for (var mode : modes.entrySet()) {
            Map<String,Result> rows = new LinkedHashMap<>();
            for (Query q : queries) {
                List<Span> ranked = mode.getValue().apply(q).stream()
                        .map(hit -> new Span(hit.getFilePath(),hit.getStartLine(),hit.getEndLine())).toList();
                assertEquals(LIMIT,ranked.size());
                Integer rank = null;
                for (int i=0; i<ranked.size(); i++) {
                    Span h=ranked.get(i), e=q.expected();
                    if (h.path().equals(e.path()) && h.start()<=e.start() && h.end()>=e.end()) {
                        rank=i+1; break;
                    }
                }
                rows.put(q.id(),new Result(q.id(),q.expected(),ranked,rank,new ArrayList<>()));
            }
            byId.put(mode.getKey(),rows);
            results.put(mode.getKey(),new ArrayList<>(rows.values()));
        }
        for (int pass=0; pass<PASSES; pass++) {
            var order = new ArrayList<>(modes.keySet());
            for (int i=0; i<queries.size(); i++) {
                Query q=queries.get(i);
                for (int j=0; j<order.size(); j++) {
                    String mode=order.get((i+pass+j)%order.size());
                    long begin=System.nanoTime();
                    var found=modes.get(mode).apply(q);
                    // Materialize projection fields in the timing window.
                    found.forEach(CodeChunkProjection::getFilePath);
                    long micros=(System.nanoTime()-begin)/1000;
                    byId.get(mode).get(q.id()).lookupMicros().add(micros);
                }
            }
        }
        Map<String,Object> report=new LinkedHashMap<>();
        report.put("corpusRevision",fixture.get("sourceRevision").asText());
        report.put("embeddingModel",fixture.get("model").asText());
        report.put("replaySha256",HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file))));
        report.put("labelsSha256",fixture.get("labelsSha256").asText());
        report.put("files",fixture.get("files").asInt());
        report.put("chunks",inserts.size()); report.put("topK",LIMIT); report.put("timedPasses",PASSES);
        report.put("postgres",sql.queryForObject("show server_version",String.class));
        report.put("pgvector",sql.queryForObject("select extversion from pg_extension where extname='vector'",String.class));
        report.put("variants",results);
        String prefix = SET.isEmpty() ? "retrieval-" : "retrieval-" + SET + "-";
        json.writerWithDefaultPrettyPrinter().writeValue(Path.of("target", prefix + (Boolean.getBoolean("eval.dev") ? "development.json" : "replay.json")).toFile(),report);
        // The 34/40 floor belongs to the original repository. A new set is reported, never gated, so it cannot be tuned against.
        if (!Boolean.getBoolean("eval.dev") && SET.isEmpty()) {
            long baselineHits = results.get("vector").stream().filter(r -> r.firstSupportingRank() != null).count();
            long candidateHits = results.get("candidate").stream().filter(r -> r.firstSupportingRank() != null).count();
            // The accepted run found all supporting lines for 34 of the 40 frozen cases.
            assertTrue(candidateHits >= Math.max(34, baselineHits),
                    () -> "Candidate " + candidateHits + "/40, vector " + baselineHits + "/40. Inspect target/retrieval-replay.json");
        }
    }
}

