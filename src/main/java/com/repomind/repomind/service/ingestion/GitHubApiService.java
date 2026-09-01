package com.repomind.repomind.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GitHubApiService {

    // GitHub's compare endpoint stops being trustworthy past this many
    // files (force-push, rebase, huge merge) — caller falls back to a
    // full re-ingest rather than trusting a possibly-incomplete diff
    private static final int MAX_DIFFABLE_FILES = 300;

    private static final Pattern REPO_URL_PATTERN =
            Pattern.compile("github\\.com/([^/]+)/([^/]+?)/?$");

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.github.com")
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record RepoCoordinates(String owner, String repo) {}
    public record ChangedFile(String filename, String previousFilename, String status, String blobSha) {}
    public record CompareResult(List<ChangedFile> files, boolean tooLargeToDiff) {}

    public RepoCoordinates parse(String githubUrl) {
        Matcher m = REPO_URL_PATTERN.matcher(githubUrl);
        if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse owner/repo from: " + githubUrl);
        }
        return new RepoCoordinates(m.group(1), m.group(2));
    }

    private WebClient.RequestHeadersSpec<?> authed(WebClient.RequestHeadersSpec<?> spec, String token) {
        return (token != null && !token.isBlank())
                ? spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                : spec;
    }

    /** Latest commit SHA on the default branch — one call, no clone. */
    public String getLatestCommitSha(RepoCoordinates coords, String token) {
        String uri = String.format("/repos/%s/%s/commits?per_page=1", coords.owner(), coords.repo());
        String body = authed(webClient.get().uri(uri), token)
                .retrieve().bodyToMono(String.class).block();
        try {
            JsonNode arr = objectMapper.readTree(body);
            if (!arr.isArray() || arr.isEmpty()) {
                throw new RuntimeException("Repo has no commits");
            }
            return arr.get(0).get("sha").asText();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse latest commit for " + coords.repo(), e);
        }
    }

    /** Which files changed between two commits — no clone needed. */
    public CompareResult compare(RepoCoordinates coords, String base, String head, String token) {
        String uri = String.format("/repos/%s/%s/compare/%s...%s",
                coords.owner(), coords.repo(), base, head);
        try {
            String body = authed(webClient.get().uri(uri), token)
                    .retrieve().bodyToMono(String.class).block();

            JsonNode filesNode = objectMapper.readTree(body).get("files");
            List<ChangedFile> changed = new ArrayList<>();
            if (filesNode != null) {
                for (JsonNode f : filesNode) {
                    changed.add(new ChangedFile(
                            f.get("filename").asText(),
                            f.has("previous_filename") ? f.get("previous_filename").asText() : null,
                            f.get("status").asText(),
                            f.has("sha") ? f.get("sha").asText() : null
                    ));
                }
            }
            return new CompareResult(changed, changed.size() >= MAX_DIFFABLE_FILES);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Compare {}...{} 404'd for {}/{} — likely rewritten history",
                    base, head, coords.owner(), coords.repo());
            return new CompareResult(List.of(), true);
        } catch (Exception e) {
            throw new RuntimeException("Compare failed for " + coords.repo(), e);
        }
    }

    /** Raw content of a file at a specific blob SHA — fetched directly, no clone. */
    public String getBlobContent(RepoCoordinates coords, String blobSha, String token) {
        if (blobSha == null) return null;
        String uri = String.format("/repos/%s/%s/git/blobs/%s", coords.owner(), coords.repo(), blobSha);
        return authed(webClient.get().uri(uri), token)
                .header(HttpHeaders.ACCEPT, "application/vnd.github.raw+json")
                .retrieve().bodyToMono(String.class).block();
    }
}