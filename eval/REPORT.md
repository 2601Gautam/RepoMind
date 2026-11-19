# RepoMind benchmark results and failure log

**Current chat prompt changed (17 November 2026):** repository scope rules now distinguish social/off-topic messages from technical questions with missing evidence. The saved generation scores below are historical and do not validate this prompt. `PromptDriftTest` correctly flags G01–G12; do not refresh their hashes without regenerating and reviewing the answers. Standalone greetings and affection use a deterministic, saved scope reply without retrieval/model calls (11 scope cases and one service test pass). Broader off-topic handling is prompt-based and has not yet been evaluated against a live model.

**Chat UX follow-up:** activity is now a circular control at the bottom of the sidebar, with a dismissible status popup. Recovery distinguishes answers actually received from requests merely started, restores answers completed while disconnected/away, and rejects stale history reads after a new question or clear. Sources are restricted to retrieved files explicitly referenced by the answer, both when streaming/saving and when rendering old saved replies; generic scope and missing-context replies show none. Verified with 64 frontend tests, a production frontend build, targeted lint, and 38 distinct Java checks (18 activity database cases, 5 resilience cases, controller detachment, 11 social cases, one social service case, 2 source-filter cases). A headless browser against a local mock API passed normal completion without a history reload, SPA navigation during generation, hard reload during generation, connection-drop recovery, saved off-topic source suppression, completed status, Escape dismissal, and desktop/mobile popup layout. The prompt-baseline failure above remains intentional and visible; no new live-model quality score is claimed.

Recorded 17 November 2026. On a frozen 40-question RepoMind benchmark, the search used by chat returned a chunk containing the complete reference code for **27/40 questions with vector search and 34/40 with identifier-aware search**. That is **67.5% to 85%, a gain of 17.5 percentage points**. The final database lookup cost increased. On a second, unseen JavaScript repository with 40 new questions, the same method went from **33/40 to 36/40 (82.5% to 90%)** with no losses; see [Second repository check](#second-repository-check-unseen-javascript-codebase). On the separate local generation exercise, only **5 to 6 of 24 answers** met the full rubric (6 on the first review, 5 on a repeat run), and the one-case debug gain did not reproduce.

[Run instructions](README.md) · [Scorecard](results/scorecard.json) · [Per-question search traces and timings](results/retrieval-paired.json) · [Per-answer review](results/generation-review.tsv)

## Search evidence

| Same 40 questions at k = 12 | Vector baseline | Final search |
|---|---:|---:|
| Complete source evidence | 27/40 | 34/40 |
| Named identifiers | 13/20 | 20/20 |
| Prose questions | 14/20 | 14/20 |
| Mean reciprocal rank at 12 | 0.2328 | 0.4400 |
| Local lookup p50 | 9.249 ms | 11.337 ms |
| Local lookup p95 | 10.530 ms | 18.610 ms |

The [frozen labels](golden/retrieval.tsv) have a question, reference fact, file and inclusive line range. Four development questions are outside the denominator. The 40 scored questions were frozen before changing the search method. The coding assistant authored the reference labels on one repository. They have no independent human adjudication. For each question a hit requires a single top-12 chunk in the correct file that covers **all** labeled lines. Reciprocal rank is 1/rank for the first such chunk and zero on a miss. This oracle can miss other valid implementations and cannot establish whether the model used the evidence.

Both paths used the same snapshot, the same 206 historical chunks from 116 source files, the same frozen query and document embeddings and the same context limit. For names resembling camelCase or snake_case, the candidate fuses the two ranked lists using reciprocal-rank fusion with constant 60. Prose without such names uses the original vector query. The separate [pre-change baseline ranks](results/retrieval-before-ranks.json) match the vector side of the final pair for all 40 questions.

- Seven gains and zero lost top-12 hits. Gains were ID04, ID07, ID09, ID10, ID15, ID18 and ID20. ID04 previously missed the method that creates the presigned download URL. The final search placed its 15-minute expiry implementation at rank 2.
- Six persistent misses were NL01 (clone depth), NL02 (chat history limits), NL05 (quota response header), NL06 (unchanged revision), NL09 (concurrent submission) and NL14 (initial listing filter). All are prose questions. They are now available for diagnosis. Use new repositories and new unseen questions for the next quality estimate.
- The initial hybrid query calculated a text-search vector from chunk content while ranking. It achieved 34/40 but the paired p95 was 10.948 ms for vector-only and 93.545 ms for hybrid. The final version stores generated identifier terms with a GIN index. Across the two runs the hybrid p95 fell from 93.545 to 18.610 ms, about **80.1%**. All 480 hybrid result positions were identical. The two p95s come from separate warmed runs, and the vector control also varied. The final vector-only query was still faster. Generating and storing terms adds index-write work and storage. [Intermediate ranks and timings](results/retrieval-hybrid-trial.json)
- Each version had one untimed pass, followed by five measured passes over all 40 questions. The order alternated within each pair. Timings use nearest-rank p50/p95 across 200 repository calls per version. Includes Java query execution and reading the projected paths, excludes embedding, generation, network streaming to a browser and concurrent user load. AMD Ryzen 7 5800HS, local Docker PostgreSQL 16.16 / pgvector 0.8.7, probe setting 100. Cache warmth and a small corpus limit performance conclusions.

The embedding replay uses quantized local all-MiniLM-L6-v2 on commit `7988a6d76bc4e82169645eabc8e15bf05f37defb`. This corpus keeps the old window boundaries to isolate ranking changes. Production uses a different embedding provider. The saved [replay](golden/minilm-replay.json.gz) and normalized labels have SHA-256 digests in the raw result.

## Second repository check (unseen JavaScript codebase)

The first 40 questions were frozen before changing the search, but they come from one repository and the search method was developed against that stack. To test whether the gain carries over, 40 new questions were written for a different codebase before any search ran on it: **InsightStox**, a Node/Express and React portfolio app (183 source files, 413 chunks, no Git history). The search code was byte-identical to the version measured above ([hashes](results/final-verification.json)). The run was executed once and not tuned.

| Same method, new repository, k = 12 | Vector baseline | Final search |
|---|---:|---:|
| Complete source evidence | 33/40 (82.5%) | 36/40 (90%) |
| Named identifiers | 17/20 | 20/20 |
| Prose questions | 16/20 | 16/20 |
| Mean reciprocal rank at 12 | 0.4594 | 0.5980 |
| Local lookup p50 / p95 | 17.0 / 18.7 ms | 28.5 / 35.3 ms |

- **Result.** Three gains (ID12, ID13, ID14) and zero losses, all identifier questions. All three were misses for vector search alone because the question names a function (getCapCategory, getPortfolioRiskFromCaps, ensureAuth) while the answer is code, not prose. Prose questions did not change. Four prose misses remain: NL02, NL13, NL14 and NL20.
- **How to read it.** The direction matches the first repository, but three questions is a small sample. A paired sign test on three gains and no losses gives p = 0.25, so this alone does not prove the gain. The 95% Wilson intervals are 68 to 91% for vector and 77 to 96% for the candidate and overlap. The baseline is also stronger here (82.5% against 67.5%) because most files are under 100 lines, so a whole file is one chunk and a top-12 list covers a large share of the 413 chunks. Less room to improve means a smaller gain. The p95 cost roughly doubled (18.7 to 35.3 ms), a larger relative increase than before.
- **Labels.** Written by the coding assistant from the code, then checked two ways. A separate agent that had not seen the labels found the same file for 44 of 44 questions (mean line overlap 0.87). The project owner reviewed 10 randomly chosen scored questions: 4 OK, 4 LOOSE, 2 WRONG. The two wrong labels were a span missing part of the answer and a cache lifetime stated more strongly than the code supports. All were fixed, along with three more found by re-auditing the other 30 for the same faults, before any search ran. A first-draft pass rate of 6 of 10 shows that AI review alone was not enough. The full history is in [REVISIONS.md](golden/insightstox/REVISIONS.md). One reviewer on one sample is not independent adjudication of all 40 labels.
- **Limits.** One additional repository and one stack. Local MiniLM embeddings, not the production provider. The corpus is a directory snapshot identified by file hashes ([sources](golden/insightstox/sources.sha256.json)) rather than a commit. The fixture stores chunks of that source, so keep `eval/golden/insightstox` private if the project is not yours to publish. [Questions and labels](golden/insightstox/retrieval.tsv) · [Scorecard](results/insightstox-scorecard.json) · [Per-question traces](results/insightstox-paired.json)

**Résumé wording.** Raised top-12 code-evidence retrieval from 67.5% to 85% on a 40-question RepoMind benchmark and from 82.5% to 90% on a second, unseen JavaScript repository, by combining pgvector similarity with identifier search (identifier questions 17 to 20 of 20 on the second repository, prose unchanged).

## Regression failures and changes

The [run manifest](results/run-manifest.json) fingerprints the measured working-tree implementation, SQL schema and tests. The candidate has no separate Git commit. The pinned source snapshot in the replay identifies the immutable corpus. A final repeat of the complete suite reproduced the 27/40 and 34/40 search counts. The 24 generator answer texts reproduced in that repeat but only 14 of 24 did in a later one (see the generation repeat), so generator answers are not fully deterministic.

| Test and baseline observation | Change | Verified result |
|---|---|---|
| AI service contracts, 10/24 passed. Empty retrieval sent two completion events. Actual tab characters broke JSON. A backslash in a debug path changed the value. A conversation could be reused with the wrong repository. Six positive chat cases also required a *new* lossless source-array contract. | Serialize events with Jackson, emit a single terminal outcome, enforce the conversation’s repository and retain the old content field alongside structured paths. Store structured paths for history. Validate interview data before writing. | 24/24 passed on fixed provider responses, including provider failure, cancellation, denied access, malformed output, short/duplicate questions and a valid control. [Baseline failures](results/contracts-before.json) · [Case definitions](golden/provider-contracts.json) |
| Chunk boundaries, 1/12 passed. Empty input produced a chunk, Windows carriage returns changed content and the old loop could emit a tail with no new lines. | Normalize physical source lines and stop once the final real line is included. | 12/12. Sizes 0 to 1,000 cover 99/100/101 and overlap boundaries. Every chunk must have exact text, correct inclusive line numbers and some previously unseen content. |
| Incremental sync, 1/4 passed. A rename left the old path. Reading a changed blob or writing a malformed embedding could still leave the revision advanced. | Stage changed files first and flush the partial batch. Replace affected paths, count chunks and commit the revision in one locked database transaction. Propagate incomplete embedding/write failures. | 4/4 on PostgreSQL. Checked stored paths, commit and content on rename, rollback after blob or dimension errors, and deletion-only with zero embedding calls. GitHub and embeddings were controlled test doubles. |
| Browser stream reader, 2/6 passed. Empty/truncated streams, malformed JSON and an exception inside the rendering callback could be silently accepted. | Surface malformed or unfinished streams and callback failures. Retain incremental decoding and cancel/release the reader on exit. | 6/6 native Node tests of the same reader imported by chat and debug, including Unicode split one byte at a time and a terminal provider error. |
| Fresh application context failed with missing column conversations.summary. | Align bootstrap and upgrade SQL with the mapped summary and source path fields. | Hibernate schema validation and application startup passed on the disposable schema. |

The full Java suite, including the model collection run, passed 55/55. The final stored-term query passed 54/54 non-model checks on a freshly recreated database. The frontend production build passed. The project-wide lint run reported 41 errors and nine warnings, the same counts measured against the original tracked frontend source under the same installed ESLint. Vite flagged a large output bundle. Remote Actions, a live browser journey, hosted model providers and AWS delivery were outside the executed checks. [Java results by suite](results/java-final.json) · [Client regressions](../frontend/test/stream.test.js)

## Real model answers and experiments

Twelve chat, six debug and six interview inputs were sent through the production prompt builder to local Qwen2.5-0.5B Instruct Q4_K_M. Each included the fixed [evidence and question](golden/generation.json). Reference rubrics stayed out of the messages. Runs used temperature 0, seed 17, a 4,096-token context and 200 output tokens for chat/debug or 640 for interview. This generator exercise uses supplied evidence and synthetic cases. It measures a CPU model on development examples reused during prompt iteration.

| Strict reviewed cases | Baseline | Retained version |
|---|---:|---:|
| Chat | 5/12 | 5/12 |
| Debug | 0/6 | 1/6 |
| Interview sessions with five supported questions and answers | 0/6 | 0/6 |
| All cases | 5/24 | 6/24 |
| Finished before the output token cap | 13/24 | 19/24 |
| Debug outputs containing all four headings | 3/6 | 6/6 |
| Structurally complete, distinct interview sets at requested difficulty | 5/6 | 6/6 |

The coding assistant scored the full response against its reference, requiring every requested fact, no material unsupported claim, a finished answer and meaningful content for required sections. For G09, naming the sender alone missed the Parser handoff explicitly included in the rubric. For G17, asking for a trace was appropriate but the prevention section supplied no advice. Each verdict records a reason and an answer SHA-256. This is a strict, non-blinded development review. The final 24 responses used 10,320 prompt and 4,534 completion tokens. Baseline totals were 10,076 and 4,739. No provider bill or user time saving was measured. [Initial answers](results/generation-before.json) · [Retained answers](results/generation-after.json) · [Case-by-case decisions](results/generation-review.tsv)

| Failed attempt or remaining case | Decision |
|---|---|
| A shorter chat instruction answered more lookups directly but converted a worker count of three into a 3% savings rate (G11). | Rejected. The original prompt abstained on that same input. [Trial answers](results/generation-trial.json) |
| An explicit fallback improved abstention but refused all ten answerable chat questions. | Rejected after comparing answerable and unanswerable slices. [Abstention trial](results/generation-abstention-trial.json) |
| Balanced examples produced another unsupported 3% rate and copied “six days” into the unrelated readiness question G06. | Restored the original chat prompt. Kept the independently verified streaming, authorization and search improvements. [Example trial](results/generation-examples-trial.json) |
| Shorter debug sections helped the missing-configuration case G15. G13 still counted the two letters EU incorrectly, G14 steered toward data recovery with no evidence of corruption and G18 treated an available timestamp as missing. | Retain the focused debug prompt and the failed cases for provider comparison. A grounded diagnosis requires better evidence use. |
| Each final interview session had the required structure. G24 claimed an ArrayList where the source only described appending and totaling entries. | Keep validation for wrong shape, duplicates and blank fields. Six of six generation sets still failed the grounding standard. Richer real snippets, claim-to-line support and independent review are the next tests before relying on generated answer keys. |

### Repeat of the generation run

Repeated on 17 November 2026 with the same pinned Qwen2.5-0.5B Q4_K_M file (SHA-256 verified), the same pinned llama.cpp image, temperature 0, seed 17 and the same 24 prompts. [Fresh answers](results/generation-rerun.json) · [Review of this run](results/generation-rerun-review.tsv)

| | First reviewed run | Repeat |
|---|---:|---:|
| Prompts identical to the reviewed run | | 24/24 |
| Answers byte-identical to the reviewed run | | **14/24** (all 12 chat, 2 of 6 debug, 0 of 6 interview) |
| Strict pass, all cases | 6/24 | **5/24** |
| Chat / debug / interview | 5/12, 1/6, 0/6 | 5/12, 0/6, 0/6 |
| Finished before the output cap | 19/24 | 19/24 |
| Debug answers with all four headings | 6/6 | 6/6 |
| Interview sets valid, distinct, requested difficulty | 6/6 | 6/6 |

The structural results reproduced exactly. The quality grade did not. Ten answers changed even with a fixed seed (G13 to G15, G17 and G19 to G24). All ten were regraded strictly against the same rubric and all ten failed. G15, the only debug pass in the first run, now tells the user to provide the missing key "in the trace", so it fails. The most likely cause is that llama.cpp results depend on how much of the prompt it reuses from the previous request, which differs with request order, but that was not isolated.

**What this changes.** The earlier claim that the retained prompts improved strict quality from 5/24 to 6/24 rests on one debug case that passed once and failed once, so it is within run-to-run noise and should not be quoted as an improvement. Chat is unchanged at 5 of 12 and the interview sets never met the grounding standard. The improvements that do reproduce are structural: answers finishing before the cap (13 to 19 of 24), debug answers with all four sections (3 to 6 of 6) and valid distinct interview sets (5 to 6 of 6). Grading was again done by the coding assistant without blinding, and G14 is borderline (a lenient grade would give 6/24 for this run).

## Interview and release decisions

**Résumé wording:** Improved top-12 code evidence retrieval from 67.5% to 85% on a frozen 40-question RepoMind benchmark by combining pgvector similarity with identifier search.

**What to demonstrate:** Open ID04 alongside NL02, then contrast G24’s parseable JSON with its unsupported claims. For resilience, run SyncSnapshotIT and explain why a single transaction protects the last complete revision. Always state the metric denominator and corpus when quoting a percentage.

**Next decisions:** Label an unseen, multi-repository set with a second reviewer, measure the configured hosted providers end to end using permitted fixtures, profile storage and ingestion time alongside p95 at higher concurrency, and test crash recovery and full rebuilds. Incremental updates currently stage changed chunks in memory. A full re-index fallback still discards the old index before its replacement is ready. The current evidence does not establish production RAG accuracy or a production throughput improvement.

## Redis benchmarks

**Two different effects were repeatable.** A five-minute per-user repository-list cache reduced PostgreSQL list queries by 95% in this repeated-read workload. For chat memory, replacing three client calls with one warmed Redis script reduced p95 append time by 60%. Indexed PostgreSQL returned an equivalent four-message window faster than Redis in both runs.

### What was compared

| Application operation | First run, p50 / p95 | Independent repeat, p50 / p95 |
|---|---:|---:|
| Repository list via the same controller with caching bypassed | 1.584 / 3.016 ms | 1.321 / 2.141 ms |
| Repository list via Spring’s Redis cache, including misses | 1.127 / 2.905 ms | 0.947 / 2.169 ms |
| Cache hit only, 570 per run | 1.101 / 2.158 ms | 0.927 / 1.448 ms |
| Cache miss plus fill, 30 per run | 4.567 / 7.721 ms | 3.472 / 5.136 ms |
| Read latest four messages via indexed PostgreSQL | 0.655 / 1.057 ms | 0.598 / 0.779 ms |
| Read latest four via the application’s Redis memory service | 0.731 / 1.254 ms | 0.667 / 0.892 ms |
| Original Redis append, trim and expire as three calls | 1.946 / 2.402 ms | 1.948 / 2.395 ms |
| Same Redis write using one script invocation | 0.729 / 0.956 ms | 0.729 / 0.957 ms |

**Repository lists.** Each run used ten users with 20 distinct repositories each. Three rounds started with ten empty cache entries. Each user requested their list 20 times per round before expiry. The benchmark alternated the proxied controller with its original uncached method and verified every DTO and its order. Hibernate recorded **600 list SQL statements versus 30**, exactly one per cache miss and zero per hit. The **95% hit rate** follows from the chosen workload. Mixed median latency improved by about 28% in both runs. Mixed p95 changed direction between runs, so the defensible résumé metric is database queries avoided. Cache fills have a cost, and more frequent updates or expiries reduce the hit rate. Single-repository status polling is a different endpoint.

**Chat reads.** Ten conversations each had 500 durable messages. The production memory service stores the latest ten with a five-day expiry refreshed on append. Chat reads four and uses the contents of the last two to rewrite a follow-up retrieval query. PostgreSQL holds the transcript and rolling summary. The SQL comparison selected only the same four roles and contents using an evaluation-only composite index on conversation, creation time and ID. Its index scan is recorded in the scorecard. Every paired result matched in content and order. The Redis path served 600 window reads per run without a transcript SELECT. Its p95 was higher in both runs. On a cold key chat currently receives an empty window, while the durable SQL comparator can still retrieve old messages. Answer quality or full chat latency was outside this comparison.

**Chat writes.** Both versions serialized the same role and content with the application mapper. The original implementation sent RPUSH, LTRIM and EXPIRE separately. The final Lua script performs those operations together on the same conversation key. On the warm path this took **one client round trip instead of three**. After warm-up, the isolated server counted exactly **600 EVALSHA calls and zero EVAL fallbacks** for 600 candidate appends per run. Both paths retained the same ten messages in the same order and renewed the five-day expiry. Local append p95 decreased **60.2%** in the first run and **60.0%** in the repeat. A fresh server loads the script on first use. The configured Redis service must permit script execution. The test excludes PostgreSQL transcript writes and LLM calls.

**How to interpret the timings.** Each run used one sequential Java client, loopback Docker services, Redis 7.4.12 and PostgreSQL 16.16. Each path received 100 warm-up operations, then three rounds of 200 measured operations. Within each pair, execution order alternated. A second Maven invocation ran the same experiment in a fresh JVM. p50 and p95 use nearest rank across the 600 observations per path. These measurements cover the controller/service call through deserialization and result materialization. They exclude HTTP, authentication and hosted network latency.

### Redis failures and fixes

| Case | Observation and action |
|---|---|
| Stale list immediately after eviction | A direct PostgreSQL read showed the new name while the next cached read still returned the old one. Spring Data Redis can defer a cache clear/write. Configured the cache writer to complete writes and evictions synchronously. Verified global invalidation and immediate removal of the requesting user’s list after a membership change. Another user’s cached list survived that targeted eviction. The benchmark was sequential. Concurrent, in-flight list fills were outside this check. |
| Outage during a memory append | Before the fix, a Redis connection exception escaped from the optional write. The memory service now catches data-access failures. The disconnected service test verifies that writes return and memory reads yield an empty list. |
| Zero or negative requested window | A zero request used Redis list offset zero and returned stored messages. The service now returns an empty result for nonpositive limits. The tests also cover corrupted JSON, conversation isolation, trimming, expiry renewal, deletion and a missing key. |
| Repository-list cache outage | With Redis pointed at an unavailable loopback port, the controller’s cache lookup propagated a data-access exception, and the same failure would have failed an ingestion whose final cache eviction could not reach Redis. **Fixed 17 November:** a `CacheErrorHandler` logs the failure and continues, so the list is read from PostgreSQL and eviction failures no longer fail the caller. A failed eviction can leave a stale list until the five minute expiry. `RedisAvailabilityIT` now asserts the fallback. |

The offline Java run passed **61/61** checks and a fresh JVM passed all **7/7** Redis-specific checks. The checks used disposable local services and synthetic rows. Remote CI and production Redis have not run this benchmark here.

[Primary scorecard](results/redis-summary.json) · [Independent repeat](results/redis-summary-repeat.json) · [Read observations](results/redis-impact.json.gz) · [Write observations](results/redis-append.json.gz) · [Repeat reads](results/redis-impact-repeat.json.gz) · [Repeat writes](results/redis-append-repeat.json.gz) · [Rerun commands](README.md#measure-both-redis-uses)

**Résumé wording.** Reduced PostgreSQL repository-list queries by 95% on a 600-read benchmark and cut local p95 chat-memory append latency by 60% by combining append, trim and expiry in one Redis script.

## SQS and direct JVM workers

The repository already had architecture notes, a LocalStack connectivity check and a fixture-generation timer. None measured queue-backed ingestion against an in-process executor at the same concurrency. This experiment used RepoMind’s real SQS publisher and consumer with LocalStack, Java 21 and real PostgreSQL/pgvector. The direct executor and queue workers called the same ingestion service.

**Recovery was the clearest difference.** With 12 jobs already accepted and four partially written, a JVM crash left a replacement direct executor with zero runnable tasks. The original SQS code recovered eight. The repaired consumer and database write path recovered **12/12**, producing **24 rows for 24 distinct (repository, file, chunk) keys**. On the short speed fixture, the direct four-thread executor finished first.

### Speed and the cost of dispatch

| Path | Median 12-job burst | Jobs/s | Submit p95 | Submit to worker start p95 | Submit to final DB commit and service return p95 |
|---|---:|---:|---:|---:|---:|
| Direct executor, 1 thread | 3.364 s | 3.57 | 0.217 ms | 3.083 s | 3.364 s |
| Direct executor, 4 threads | 0.855 s | 14.04 | 0.186 ms | 0.574 s | 0.860 s |
| SQS emulator, 1 worker | 11.532 s | 1.04 | 5.811 ms | 11.188 s | 11.480 s |
| SQS emulator, 4 workers | 2.373 s | 5.06 | 5.506 ms | 2.050 s | 2.340 s |

- **Worker-count effect.** Four SQS workers raised measured burst throughput from 1.04 to 5.06 jobs/s. Direct dispatch went from 3.57 to 14.04 jobs/s. These are independent repositories processed concurrently. An individual repository still runs on one worker. The queue’s 4.86× burst ratio includes the polling schedule. Twelve small jobs at one per poll need 11 waits between polls. At four per poll they need two. This ratio cannot be extrapolated to steady-state production traffic.
- **Transport effect.** At four workers the direct path had a 0.855 s median burst and SQS had 2.373 s. Direct submission enqueues in process memory. SQS submission serializes a message and waits for the broker’s response. Its consumer checks available semaphore permits, receives only as much work as it can start, and waits the configured one second before another poll. A full pool leaves additional messages at the broker. Queue publication, receive, deletion and redeliveries introduce external requests. Cloud cost, provider rate limits, CPU saturation and AWS network latency were not measured.
- **Measurement boundaries.** Four warm-up jobs per arm, then three ordered/rotated bursts of 12 per arm (36 measured jobs per arm, 144 per run). Each arm starts with the burst submitted before draining. The timer starts with the first submission and ends after the last job finishes its DB write and returns from the service. Individual submit/start/finish timings start at that job’s submission. Submit p95 excludes controller auth, HTTP and initial job-row creation. Completion excludes the final SQS delete request. p95 uses nearest rank over 36 jobs per arm. The jobs/s value is 12 divided by the median burst duration. Worker concurrency was observed and checked against each configured cap.
- **What was real.** Lease acquisition, file chunking, Redis invalidation, PostgreSQL batch writes, terminal status, the AWS Java SDK and LocalStack’s HTTP queue. Both arms used two tiny source files, fixed 1,024-dimensional embedding vectors, a no-op archive and 250 ms of controlled clone delay. Workers used a batch of one chunk so the crash could occur after a real partial commit. The speed test retained the application’s 1,000 ms poll delay, 20 s long-poll setting and 1,200 s visibility. The emulator image digest is pinned in [Compose](compose.yml). Production repository sizes, live GitHub, S3, embeddings and AWS SQS service availability are outside the measurement.

### Crash and retry evidence

A fresh JVM repeated the speed comparison. Median batch times were 3.409 s (direct, one worker), 0.863 s (direct, four), 11.535 s (SQS, one) and 2.379 s (SQS, four). The four-worker queue completed 5.04 jobs/s in the repeat and 5.06 jobs/s in the first run. [Repeat scorecard](results/queue-summary-repeat.json) · [Repeat job timings](results/queue-speed-repeat.json)

The parent seeded 12 PENDING rows. A separate JVM accepted all 12 jobs using either the fixed executor or our SQS publisher. The child stopped abruptly with four workers each blocked just after their first committed chunk. This bypassed graceful shutdown. PostgreSQL and the queue process stayed online. Both arms then showed 8 PENDING, 4 PROCESSING and 4 stored chunks. A fresh executor had no queue scanner or manual resubmission. For the SQS arm, a new consumer polled the original queue. The direct arm was observed for 13 seconds. The queue arm had up to 13 seconds to complete. The restart test used a two-second visibility timeout, an eight-second worker lease and accelerated polling. Its elapsed recovery time is specific to those settings.

| Restart path | READY / accepted | Database outcome |
|---|---:|---|
| Direct JVM executor | 0/12 | Database retained 8 PENDING, 4 PROCESSING and 4 partial chunks. The executor’s pending tasks disappeared with its process. |
| SQS before repair | 8/12 | 4 PROCESSING rows stayed stranded. The queue acknowledged their redeliveries while the database still considered the dead worker’s leases active. |
| SQS after repair | 12/12 | 0 PENDING, 0 PROCESSING, 0 FAILED. All 24 expected chunk keys appeared once. |

A second pair of deliberately terminated child JVMs reproduced 0/12 automatic direct completions and 12/12 queue completions, with 24 distinct chunks again. The first and second repaired SQS recovery observations were 8.61 s and 8.10 s after starting the replacement consumer under accelerated timers. [Independent crash repeat](results/queue-crash-repeat.json)

| Failure or edge case | Fix and evidence |
|---|---|
| Only surviving delivery arrives before the lease expires | The old consumer deleted it. A busy lease now defers that receipt using ChangeMessageVisibility and returns without acknowledging it. A terminal or deleted job can be acknowledged. The local IAM setup now documents permission for this call. |
| Worker dies after committing its first file | Independently reproduced an old retry producing First.java twice. Claim and removal of partial chunks now share one transaction. Each subsequent batch, progress update and terminal write checks the current lease under the repository row lock. |
| An old worker tries to write after takeover | The replacement lease fences the old worker’s chunk append and terminal update. The replacement retains ownership. |
| Duplicate after READY, or an acknowledgement fails | The existing terminal state prevents re-indexing. The tests compare stored chunk IDs and verify that the receipt disappears after visibility expires. |
| Publisher cannot send, clone fails, or payload is malformed | Publish errors propagate to the caller. Clone errors now remain retryable; see the failure-classification follow-up below. A malformed payload reaches the test queue’s configured dead-letter queue after repeated deliveries. Queue-level redrive is configured separately from application code. |

The initial repair fenced database writes but retained a fixed-duration lease. The follow-up below adds renewal and measures the long-running overlap that this left open. A transactional outbox would address the separate crash window between initial database insertion and first successful publication. Production recovery can take longer with the default 20-minute timers. Amazon SQS Standard allows duplicate deliveries. [AWS delivery semantics](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html) · [AWS visibility controls](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html)

**Résumé wording.** Hardened SQS-backed ingestion to recover 12/12 accepted jobs after a forced JVM crash, up from 8/12, with zero duplicate indexed chunks in a local integration benchmark.

[Speed scorecard](results/queue-summary.json) · [Per-job timings](results/queue-speed.json) · [Original crash outcome](results/queue-crash-before.json) · [Repaired crash outcome](results/queue-crash-after.json) · [Rerun commands](README.md#compare-sqs-with-direct-jvm-execution)

The earlier offline regression run passed 68 checks, including all seven queue recovery cases. The speed and crash experiments also passed in two separate runs. The [verification record](results/queue-verification.json) names the cases and hashes the tested source files. The AWS permission change is documented in SETUP_AWS.md and AWS_SERVICES_EXPLAINED.md. The application changes remain local to this checkout.


## Renewable ingestion ownership and visibility

Follow-up on 17 November 2026. These are implemented application changes, verified against PostgreSQL and LocalStack with the real ingestion service. The earlier dispatch timings remain measurements of the earlier revision; this follow-up measures correctness and recovery, not a new throughput improvement.

| Reproduction | Before | After |
|---|---|---|
| Clone blocked beyond a 2-second lease, with 1-second visibility and a second consumer | 2 clone starts for one repository; regression assertion failed | 1 clone start, READY, exactly 2 expected chunks. Original receipt stays invisible after 3.1 seconds; an independently published duplicate still cannot enter clone. |
| Retry of a crashed partial index | Earlier reproduction retained an obsolete First.java chunk | Atomic claim deletes partial chunks before replay; expected First.java and Second.java only. |
| Receipt arrives while the database lease remains active | Earlier crash benchmark stranded 4/12 jobs by acknowledging busy deliveries | Busy receipt is deferred without deletion; the sole-delivery recovery test verifies it remains available. |
| Expired or superseded worker writes or renews | Fixed expiry permitted overlapping external work | Stable random owner token plus row-locked lease checks reject stale renewal, append and terminal writes. Expired owners cannot revive themselves even before takeover. |
| Progress saved from a stale in-memory entity | Could overwrite an independently extended expiry if saved unchanged | Progress retains the current database expiry; completion clears both token and lease. |

### What changed and why

- FullIngestionStore assigns a UUID owner on each successful claim. Token identity stays stable while the expiry moves; every batch, progress update and renewal verifies that identity and an unexpired PROCESSING lease under a row lock.
- IngestionHeartbeat renews the database lease every one-third of its duration, including while clone or embedding is blocked. Renewal failure interrupts the worker; subsequent database writes still require valid ownership. Closing the heartbeat synchronizes with callbacks so a late callback cannot interrupt that pooled thread's next job.
- IngestionQueueConsumer renews the active receipt's visibility every one-third of its configured duration. Visibility failure is logged; the database lease remains the authority for claiming and writing. All renewal schedules are cancelled when processing ends.
- Transactional cleanup and busy-receipt handling from the preceding repair remain in place. The initial policy acknowledged clone failures as FAILED. The failure-classification follow-up below replaces that policy with retained retries; only explicitly recognized permanent input outcomes are terminal.

### Reproduction and evidence

The baseline log records the expected-one/actual-two failure before renewal was implemented. The final fixture additionally checks receipt invisibility. It blocks clone with a latch for at least 3.6 seconds across two consumers; it uses controlled external I/O and real local database/queue calls. This is one deliberately adverse scenario, not an estimate of the production duplicate rate.

See [renewal verification](results/queue-renewal-verification.json), [baseline failure](results/queue-long-before.txt), and [crash recovery after renewal](results/queue-crash-renewal.json). The final regression passed 70 checks with zero failures, errors or skips, including nine queue recovery cases and the long-running receipt test. Source hashes are recorded in the verification file. The crash experiment again requires 12/12 queue completions and 24 distinct chunk keys after abruptly killing a worker JVM.

### Deployment and remaining limits

Apply migrations/20261117_add_ingestion_owner.sql before deploying this revision. New installations get the column from init.sql. Stop and drain old workers during rollout: older binaries do not participate in token ownership or renewal. The migration was applied only to the disposable local evaluation database. The consumer role requires sqs:ChangeMessageVisibility.

Renewal adds periodic SQL transactions and SQS calls; production overhead was not measured. A process crash stops renewal and permits recovery after expiry. Long GC pauses, database outages or uninterruptible external calls can still cause duplicate external work; fenced database writes prevent a superseded owner from committing through this path. Local machine-clock timestamps also assume synchronized application clocks. SQS limits visibility to 12 hours from receipt; this implementation does not checkpoint and split work beyond that limit. See [AWS heartbeat guidance](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/best-practices-processing-messages-timely-manner.html). An outbox for initial publication remains separate work.

**Interview wording.** I first reproduced two workers cloning the same repository when a fixed lease expired. Renewable database ownership and receipt visibility reduced clone starts from two to one in that forced-overlap fixture. I also verified stale-worker rejection, clean replay of partial chunks, retained busy deliveries and recovery of all 12 queued jobs after a forced JVM crash. These are local fault-injection results, not production performance claims.

## Retrying database and embedding failures

Follow-up on 17 November 2026. The earlier lease repair did not fix the broad catch block in IngestionService: it could record FAILED and return normally after a provider or SQL error, allowing the consumer to delete the message. Two new failure reproductions both failed before the change: the original queue delivery was missing after the first failed attempt.

| Scenario | Before | Final behavior |
|---|---|---|
| Embedding failure on the second file | Original receipt deleted; no automatic retry | PENDING with last error, receipt retained. Restoring the provider lets the original job reach READY with exactly First.java and Second.java. |
| PostgreSQL write error on the second chunk | Original receipt deleted after FAILED was persisted | Real trigger raises SQLSTATE 40001. First chunk remains from the interrupted attempt, receipt survives; removal of the fault permits clean replay and exactly two chunks. |
| Database also refuses the retry-state write | Not covered by the original reproduction | Real trigger rejects the PENDING update. PROCESSING and ownership remain; receipt survives. After fault removal and forced lease expiry, the original job recovers. |
| Provider failure persists across deliveries | Lost work under the previous broad catch | Message reaches the configured local DLQ after repeated receives. Partial chunks are cleared on each claim. |
| Provider configuration error | NonTransientAiException was retried in the local retry loop | No local batch-to-single fan-out; receipt retained for bounded queue redelivery and operator intervention. |
| No supported source files | Known terminal input outcome | FAILED persisted, no embeddings or chunks, receipt acknowledged. |
| Delayed visibility renewal overlaps attempt completion | Cancellation did not wait for an in-flight callback | Callback and cancellation share a lifecycle lock. A latch-based test proves shutdown waits for the callback and no failed message is deleted. |

### Implementation

FullIngestionStore.releaseForRetry checks the current, unexpired owner under a row lock, records PENDING and the last error, and clears ownership. The heartbeat has stopped before this runs. The service then throws, so the consumer cannot acknowledge the failed attempt. If release itself fails or the owner has expired, the exception still reaches the consumer and the existing lease expires naturally. Stale workers cannot release a replacement owner's job.

Only the explicit no-supported-files outcome becomes terminal FAILED here. Database, embedding, clone and other unclassified processing exceptions are retained for retry. This conservative rule avoids losing work but requires DLQ monitoring for permanent configuration errors or software defects. Local embedding backoff now catches Spring AI TransientAiException; NonTransientAiException skips local fan-out. A separate service test makes the first embedding call fail and the second succeed, verifying exactly one chunk append without releasing the job. See [Spring AI exception semantics](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/retry/package-summary.html).

### Verification and failure ledger

The final regression passed **78 checks with zero failures, errors or skips**. Retention improved from **0/2 to 2/2 deliberately injected failure scenarios**; both subsequently completed with exactly two expected chunks. This is a correctness sample, not a production success-rate estimate. The forced JVM crash again recovered **12/12** SQS jobs with **24 distinct chunks**.

The first revised run passed the SQL/embedding recovery paths but failed two single-poll availability checks (provider configuration failure and lost acknowledgement). Those failures are preserved. Visibility cancellation was synchronized with in-flight callbacks and a deterministic shutdown test added. Availability assertions now use a bounded three-second long poll after visibility expiry; they still require the original message, completed replay and exact chunk contents. Final passes do not erase the intermediate failures.

[Original failed assertions](results/queue-failure-before.txt) · [Intermediate failed checks](results/queue-retry-intermediate.txt) · [Final cases and source hashes](results/queue-retry-verification.json) · [Crash outcome](results/queue-crash-retry.json)

### Operational behavior

Retry uses the remaining SQS visibility interval; this change does not add exponential queue backoff or immediately republish messages. Local embedding retries remain bounded by ingestion.max-embed-retries. Queue retries are bounded by a configured redrive policy and message retention. The database PENDING state is not itself a scheduler.

The app does not consume the DLQ or reconcile database state when SQS moves a message there. A repository can remain PENDING with its last error, or PROCESSING after a failed database release, until an operator corrects the cause and redrives the message. Configure DLQ alarms and retention. maxReceiveCount counts receipts, including busy deliveries; it is not an exact application-attempt counter. See [AWS redrive guidance](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html).

No extra schema change is needed for the retry follow-up beyond the earlier ingestion_owner migration. The original visibility/lease mismatch protection remains active; equality of the timer values is not the correctness mechanism. These tests use a one-second visibility setting, with a longer database lease, and the existing long-running test forces a two-second lease. Production timings and throughput after this change are unmeasured.

## Embedding retries and provider rate limits

Follow-up on 17 November 2026. The question was how embedding retries behave when the provider rate limits (HTTP 429), and whether the earlier retry change helped. Tracing it found three different behaviours for one provider and a regression in the middle one.

**What the real libraries do (measured, Spring AI 2.0.0 with the Mistral client).** Every 4xx response, including 429, becomes `NonTransientAiException`; only 5xx becomes `TransientAiException`. The Mistral client words messages as `HTTP 429 - ...`, not `429 - ...`. Three retry layers stack: Apache HttpClient 5 silently re-sends a 429 or 503 once after at least a second (honoring `Retry-After`); Spring AI's retry template retries 5xx and I/O errors and its `max-attempts` counts retries, so 3 means 4 attempts and the default of 10 means 11; the application then adds its own loop. Against a fake provider, one failing embedding call cost 2 HTTP requests on a 429 and 8 on a 5xx with the settings now in `application.yml`; with the library defaults a 5xx outage would hold one call for about 19 minutes. [Real-client test](../src/test/java/com/repomind/repomind/evaluation/EmbeddingProviderIT.java)

| Where | Behaviour before this change | Consequence |
|---|---|---|
| Original ingestion code | Retried every 4xx with 1, 2, 4, 8 s waits, did not retry 5xx, fell back to one call per chunk and skipped chunks that failed | Under a provider outage or a bad API key the job finished as READY with **no chunks**. A bad key cost 2,200 calls for four small jobs |
| Ingestion after the earlier retry follow-up | Retried only 5xx; a 429 ended the attempt at once and the job was released and restarted from scratch | **Under a sustained rate limit no job completed.** Every job reached the dead-letter queue after three receipts. Exhausted 5xx still fanned out to one call per chunk |
| Incremental sync | Still the original loop | Same silent-loss behaviour |
| Chat and debug question embedding | No retry | One 429 failed the user request |

**Change.** One `EmbeddingRetryPolicy` for all four paths. It classifies errors from the status in the message (429 rate limited; 5xx, 408, 425 and I/O errors transient; 400, 401, 403 and anything unrecognized fatal and never retried). Retries use jittered exponential backoff (1 s base, 30 s cap, 8 attempts, 180 s budget per call). After a 429 a shared pacer delays every worker that shares the bean, so four workers do not keep hitting a limited provider, and it eases after successes. Exhaustion throws `RetryExhaustedException`, which callers never answer with more calls, so there is no one-call-per-chunk fan-out. Chat and debug use an interactive profile (3 attempts, 8 s budget) and then fail so the page can say so. `spring.ai.retry.max-attempts` is bounded to 3 so the layers do not multiply. Retry-After is honored once by the HTTP client but not by the application policy. [Policy](../src/main/java/com/repomind/repomind/service/ingestion/EmbeddingRetryPolicy.java)

**Benchmark.** Three behaviours against a simulated provider: a token bucket allowing about 1.2 requests per second, a 15 s outage followed by that limit, and a permanently bad key. Four jobs of 10 batches of 10 chunks, each released and restarted up to three times as the queue does. Time is scaled 1/50 and reported in equivalent seconds. Median of three runs. [Raw results with minimum and maximum](results/embedding-retry-bench.json) · [Test](../src/test/java/com/repomind/repomind/evaluation/EmbeddingRateLimitBenchIT.java)

| Scenario (4 jobs) | Metric | Original | Before this change | New policy |
|---|---|---:|---:|---:|
| Sustained rate limit | Jobs completed | 4/4 | **0/4** | 4/4 |
| | Rejected provider calls | 55 | 12 | 39 |
| | Equivalent time to finish | 44.7 s (41 to 47) | gave up | 50.3 s (50 to 96) |
| Outage then rate limit | Jobs completed | 4/4 | 0/4 | 4/4 |
| | Chunks silently lost | **400 of 400** | 0 | 0 |
| Bad API key | Provider calls | **2,200** | 12 | 12 |
| | Chunks silently lost | **400 of 400** | 0 | 0 |

**How to read it.** The new policy matters most against the other two behaviours. Against the intermediate version it takes sustained-rate-limit completion from 0 of 4 to 4 of 4, and against the original it removes the silent loss (400 of 400 chunks) and cuts a bad-key run from 2,200 calls to 12. It is not faster than the original on a plain rate limit: the original already completed, the new policy made 24% fewer calls and 29% fewer rejected ones, and its median time was about 12% longer, with one slow run at 96 s. Single-worker rate limiting was the same story (17 calls against 14). The "before this change" arm reaches the dead-letter queue within milliseconds in the simulation because redelivery is not simulated; in production each redelivery waits out the 20 minute visibility timeout.

**Limits.** The provider is simulated: its rate, latency and outage length are assumptions, not Mistral's measured limits, and job size is small. The benchmark counts application-level calls; the HTTP stack adds up to two requests each, equally for all three. The pacer state is per application instance. Parameters were chosen before the benchmark and not tuned to it. A chunk the provider rejects as invalid input (HTTP 400) still fails its job rather than being skipped, and the dead-letter queue is still not consumed by the application.

## Failure visibility and recovery after a reload or network drop

Follow-up on 17 November 2026. Before this change `IngestPage` showed the stored `errorMessage` of a FAILED repository, but `DashboardPage` cleared failed jobs without showing why and `AllReposPage` filtered them out. A reload or re-login also lost track of an ingestion, chat, debug or interview request that was still running.

| Scenario | Before | After |
|---|---|---|
| Repository ingestion FAILED | Reason visible only on the ingestion page | Reason shown on the dashboard, repository list, chat page and ingestion page through one shared notice, with the repository name |
| Repository PENDING with a stored error (queued retry) | Looked like a normal queued job | Labeled "Awaiting retry" with the last error |
| Browser closed, reloaded or user logs back in | Page state lost; no way to see running work | One authoritative snapshot from `GET /api/activity` on every page load; in-progress and failed jobs reappear |
| Network drops while an AI stream is open | Spinner or a raw error | Request recorded server-side as INTERRUPTED (or UNKNOWN if its heartbeat stopped); the app says to check saved results before resending and never resubmits automatically |
| Connection returns | User had to refresh | Polling continues through failures and also fires on online, focus and tab-visible events; banner cleared within one 5 s poll in a live browser check |
| Same request sent twice | Model could run twice | `X-Request-ID` is unique in `ai_activity`; a repeat returns 409 |

**Implementation.** `ActivityService` records only id, user, repository, kind, status, result id and timestamps in `ai_activity` (no prompts or tokens), renews a heartbeat every 10 s while a request runs, and reports a RUNNING row with no heartbeat for 60 s as UNKNOWN. Streams end COMPLETED only after a `done` event; a provider error, an exception, a client cancel or a stream that ends without `done` never counts as COMPLETED. The browser keeps a small per-user journal of request ids (no content) so a request the server never recorded shows as unconfirmed. Rows are visible only to their owner and only while the user still has the repository; they are purged after 7 days. Apply `migrations/20261117_add_ai_activity.sql` before deploying; new installs get it from `init.sql`.

**Verification.** `ActivityRecoveryIT` passes 12 cases on real PostgreSQL: completed stream with result id, provider error event, thrown exception, client disconnect, stream ending without done, malformed output, duplicate request id (409, model called once), interview success and failure, stale RUNNING shown as UNKNOWN, a 12 s request kept RUNNING by its heartbeat, owner isolation and removed-repository filtering, and 24-hour visibility plus purge. `frontend/test/recovery.test.js` passes 14 cases covering failure notices, journal expiry, corrupt storage, the journal storing identifiers only, task reconciliation, and the poller (continues after failure, coalesces overlapping refreshes, discards a response that arrives after logout). A live browser run against a mock API confirmed the failure reason on the dashboard and repository list, the connection-lost banner, automatic recovery within one poll, and an INTERRUPTED chat shown after a fresh page load. [Final verification record](results/final-verification.json)

**Limits.** Interrupted streams are not resumed and debug output is not stored server-side, so a closed debug page cannot be reopened. The live browser check used a mock API, not the Spring backend. Polling cost (one lightweight query per active user every 5 s) was not load-tested. Heartbeat and activity writes add one insert and one update per AI request; their latency impact was not measured.

**Found by hand after the first release candidate.** Running the app showed the Repositories page empty ("No repositories added yet"), no progress percentage on the dashboard and no activity panel, while the Chat and Debug pickers still listed every repository. Three separate causes:

1. **The activity provider never started (the cause of the empty pages).** It looked for `user.id`, but the real `/api/auth/me` returns the id as `userId`. My mock backend had used `id`, which hid the mistake. With the real response shape the provider stayed on its empty default forever, with no error, so the Repositories page, dashboard progress and recovery were all permanently blank. The Chat and Debug pickers call the older `/api/repos` directly and were unaffected. Reproduced exactly against a mock that returns the real shape, then fixed with a helper that reads `userId` (and `id` as a fallback), covered by tests for both shapes.
2. **Version skew (a second, separate weakness).** A frontend ahead of its backend, or a database without the `ai_activity` table, would also blank those pages because everything depended on `/api/activity`. When that endpoint answers 404 or 5xx the frontend now falls back to `/api/repos` plus the uncached per-repository status for running jobs, and says so in one line in the activity panel (offline, expired-session and rate-limit errors are still reported). The backend returns the repositories even if the activity query fails, and no longer fails a chat, debug or interview request when it cannot write its tracking row.
3. **"Recent Chats" had never worked.** The original code read a `recent_chat_repos` list that nothing wrote. `ChatPage` now records each ready repository it opens.

Running ingestions are also polled every 3 s instead of 5 s. The percentage itself was never a rendering bug: the repository cards and progress panel draw it whenever the data arrives. Verified with 12 new frontend tests, 4 new backend tests and in a browser against a mock using the real `/me` shape (50%, then 83% as the mock advanced; also with 404 and 500 activity responses). Deploy the backend and apply the migrations before, or together with, the frontend.

**Found by hand, second round (chat and debug pages).** Two more problems, both reproduced before fixing against a mock backend:

1. **The chat page flashed to a spinner and reloaded after every answer.** When an answer finished, the activity list showed it as COMPLETED, and the page treated any completed chat as a reason to re-read the whole saved history behind a full-page spinner, including for answers it had just streamed itself (the input disappeared and came back, and a history request followed every chat). Now only the first visit to a repository in a browser session shows the spinner and reads the history; later visits reuse the live conversation. A re-read happens only for a chat that finished somewhere this session did not start (another device, or before a page reload), and it is silent.
2. **Leaving the page cancelled the answer.** The page aborted its request when it was left. The server saw the disconnect, marked the request INTERRUPTED and never saved the answer, because the answer is saved only when generation completes. Two fixes. In the browser, conversations and debug analyses now live in a store outside the React page (`state/liveStreams.js`), so the stream keeps running while the user is on another page and the finished answer, sources and form are there on return. On the server, the chat endpoint is detached from the browser connection (`ActivityService.streamDetached`), so even a page reload or closed tab lets the answer finish and be saved; the record ends COMPLETED, and a provider that stays silent for 2 minutes is failed instead of running forever. Debug has no server-side history, so only the browser fix applies to it. Interview generation already finishes and saves on the server and is unchanged.

Verified with 26 new frontend tests (store behaviour: stream survives unmount, mid-answer return, duplicate sends refused, errors, rate limit, saved history never overwriting a live answer, per-user isolation), 6 new `ActivityRecoveryIT` cases on real PostgreSQL (client leaves and the answer still completes, error and duplicate handling, silent provider failed), a resilience test and a controller test that chat uses the detached stream. In a browser against a mock: no spinner and no history request after an answer; leaving mid-answer and returning later showed the complete answer with no interruption message for chat and for debug (form kept); a hard reload mid-answer showed "A chat request is running", then the saved answer appeared silently. The server-side half was tested with controlled streams, not a real model or a real browser disconnect through Spring MVC.

## Re-run after all changes

The complete Java suite (**132 checks, 0 failures** on the final tree; the local-model generation run is excluded) and the frontend suite (58 tests, build passing, lint 34 errors and 6 warnings, all in untouched files) were re-run against the final code on 17 November 2026. Deterministic results reproduced exactly: search 27/40 and 34/40 with MRR 0.2328 and 0.44 and the same seven gains; 600 versus 30 repository-list queries; 600 EVALSHA calls and zero fallbacks; 12/12 jobs and 24 distinct chunks after a forced JVM crash; queue throughput 1.04, 5.07, 3.54 and 13.82 jobs/s for SQS with one and four workers and direct execution with one and four threads (saved 1.04, 5.06, 3.57, 14.04). Timing-only values drifted with machine load: the Redis append p95 reduction was 55.9%, 58.9% and 58.7% in three later runs against 60.2% and 60.0% earlier, so quote it as about 56 to 60%.

One intermediate full run failed a single check, `QueueRecoveryIT.cloneFailureIsRetainedForRetryAndThenCompletes`. It used one short SQS poll, which LocalStack can answer with nothing while a message is visible (two of four reruns failed). Changing only that check to a bounded three-second long poll, as the other availability checks already do, gave six passes in six reruns, and the next full run passed everything. The message was never missing; the earlier failure was the test, not the application.

Generation was re-run after the other work (see the repeat in the generation section): the prompts are identical to the reviewed run (`PromptDriftTest` now fixes JSON key order; `Map.of` iterated differently per JVM and made an earlier version flaky), structural results reproduced exactly, and the strict quality grade was 5/24 against 6/24, so the earlier one-case debug gain is not a reliable improvement.

The earlier section "Retrying database and embedding failures" describes the retry loop as it stood then. Its rate-limit behaviour is superseded by "Embedding retries and provider rate limits" above.

No extra schema change is needed for the retry follow-up beyond the earlier ingestion_owner migration.
