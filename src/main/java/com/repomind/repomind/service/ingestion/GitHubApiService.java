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

    /**
     * @param tooLargeToDiff true when the diff is too big to trust (file
     *                       count, or the base commit is gone entirely —
     *                       a 404).
     * @param diverged       true when GitHub's compare endpoint itself
     *                       reports {@code status: "diverged"} — the base
     *                       commit is still reachable (so no 404), but the
     *                       two histories have split (force push, rebase,
     *                       amended commit). GitHub computes the returned
     *                       {@code files} from the merge-base, not from a
     *                       direct base→head diff, so it does not reliably
     *                       represent everything that changed relative to
     *                       what's actually indexed. Callers should treat
     *                       this the same as tooLargeToDiff — fall back to
     *                       a full re-ingest rather than apply a diff that
     *                       can silently under-represent what changed.
     */
    public record CompareResult(List<ChangedFile> files, boolean tooLargeToDiff, boolean diverged) {}

    public record RepositoryMetadata(boolean isPrivate) {}

    /**
     * Raised only when GitHub says the supplied credentials cannot see a
     * repository.  The message deliberately contains no token or repository
     * details, so it is safe to use for a generic forbidden response.
     */
    public static class RepositoryAccessDeniedException extends RuntimeException {
        public RepositoryAccessDeniedException() {
            super("GitHub token cannot access this repository");
        }
    }

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

    /**
     * Reads metadata for exactly the owner/repository contained in the GitHub
     * URL. GitHub returns 404 for private repositories that a token cannot
     * access, so 401/403/404 must all be treated as an authorization failure.
     * This is a single metadata request; it does not clone or ingest anything.
     */
    public RepositoryMetadata getRepositoryMetadata(String githubUrl, String token) {
        RepoCoordinates coords = parse(githubUrl);
        String uri = String.format("/repos/%s/%s", coords.owner(), coords.repo());
        try {
            String body = authed(webClient.get().uri(uri), token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode response = objectMapper.readTree(body);
            JsonNode privateNode = response.get("private");
            if (privateNode == null || !privateNode.isBoolean()) {
                throw new RuntimeException("GitHub did not return repository privacy metadata");
            }
            return new RepositoryMetadata(privateNode.asBoolean());
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                // Do not log the response body: GitHub may include request data
                // and this path is called with a user-supplied credential.
                throw new RepositoryAccessDeniedException();
            }
            throw e;
        } catch (RepositoryAccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not read repository metadata from GitHub", e);
        }
    }

    /**
     * Fail closed if GitHub rejects the token or cannot be reached. The caller
     * must not create a UserRepo mapping unless this returns true.
     */
    public boolean canAccessRepository(String githubUrl, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            getRepositoryMetadata(githubUrl, token);
            return true;
        } catch (RepositoryAccessDeniedException e) {
            return false;
        } catch (Exception e) {
            // A transient GitHub failure must never become an authorization
            // bypass. Keep the log free of token and request-body data.
            log.warn("Could not verify GitHub repository access");
            return false;
        }
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

            JsonNode root = objectMapper.readTree(body);

            // GitHub's three-dot compare reports one of:
            //   "ahead"     — head is a fast-forward of base (the normal case)
            //   "behind"    — head is actually behind base
            //   "identical" — no difference
            //   "diverged"  — base is no longer an ancestor of head (force
            //                 push, rebase, amended commit, etc. — the old
            //                 base commit is still reachable, so this does
            //                 NOT 404 the way a fully garbage-collected
            //                 rewrite does)
            // A is computed from the merge- "diverged" result's files[]base,
            // not from base directly, so it is not a reliable incremental
            // diff against what this repo has indexed. Flag it and let the
            // caller fall back to a full re-ingest instead of silently
            // trusting a diff that can misrepresent what actually changed.
            String status = root.has("status") ? root.get("status").asText() : null;
            boolean diverged = "diverged".equals(status);

            JsonNode filesNode = root.get("files");
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

            if (diverged) {
                log.warn("Compare {}...{} for {}/{} reports status=diverged — history was rewritten " +
                                "but the old base commit is still reachable (no 404). Treating as " +
                                "untrustworthy for an incremental diff.",
                        base, head, coords.owner(), coords.repo());
            }

            return new CompareResult(changed, changed.size() >= MAX_DIFFABLE_FILES, diverged);
        } catch (WebClientResponseException.NotFound e) {
            log.warn("Compare {}...{} 404'd for {}/{} — base commit no longer reachable " +
                            "(likely rewritten history that has since been garbage-collected)",
                    base, head, coords.owner(), coords.repo());
            return new CompareResult(List.of(), true, false);
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