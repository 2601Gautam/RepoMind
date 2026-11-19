# Running the RepoMind evaluation

[Measured results and failure log](REPORT.md) · [Reference questions and source lines](golden/retrieval.tsv) · [Recorded scores](results/scorecard.json)

Run from the repository root. The fast suite requires Java 21, Maven, Docker Compose, Node 24 and npm. The checked-in vectors allow the search benchmark to run without a model download or provider key. The database and Redis bind to loopback. Benchmark tables live in a disposable database.

## Repeat the regression suite

~~~powershell
docker compose -f eval/compose.yml up -d --wait
mvn '-Dspring.profiles.active=eval' '-Dtest=*Test,*Tests,RetrievalReplayIT,SyncSnapshotIT,RedisImpactIT,RedisAppendIT,RedisAvailabilityIT,RedisMemoryWindowIT' test
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run build
python eval/score.py --retrieval target/retrieval-replay.json
docker compose -f eval/compose.yml down
~~~

The score command recomputes ranking from every returned path and line range, then checks the saved answer-review hashes and prints the reviewed generation scores. Repeated timings vary by machine. The search test fails if the candidate loses to the simultaneous vector baseline in total hits or drops below the accepted 34/40 regression floor. Inspect per-question gains and losses even if the gate passes. The four DEV questions can be run separately with Maven property `-Deval.dev=true`. The GitHub Actions workflow runs the fast suite and uploads its JUnit and retrieval reports. The full local suite also ran the generator described below.

## Measure both Redis uses

For the Redis-specific measurements, see [Redis results in the failure log](REPORT.md#redis-benchmarks).
The following commands run the same application methods against a local Redis and PostgreSQL instance.

~~~powershell
docker compose -f eval/compose.yml up -d --wait
mvn '-Dspring.profiles.active=eval' '-Dtest=RedisImpactIT,RedisAppendIT,RedisMemoryWindowIT,RedisAvailabilityIT' test
python eval/score_redis.py target/redis-impact.json --append target/redis-append.json
docker compose -f eval/compose.yml down
~~~

To audit the saved run without starting services, use `python eval/score_redis.py eval/results/redis-impact.json.gz --append eval/results/redis-append.json.gz`. The two benchmark tests check matching outputs, observed query counts, cache invalidation, bounded memory and warmed script calls. Timing is reported for comparison between runs. Use the dedicated Compose database because the benchmark exercises the application's global repository-list invalidation, seeds synthetic rows and temporarily creates an index for the SQL comparator.

## Repeat the real local generation run

Start the model server as described below. The combined suite also needs the optional SQS service provided by the Compose `queue` profile.

Download the Q4_K_M GGUF file from the pinned [Qwen release](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/tree/9217f5db79a29953eb74d5343926648285ec7e67). Set the value below to its absolute path. Its SHA-256 is `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`. Start the same CPU inference server, wait for its health check, then run the test.

~~~powershell
$modelFile = (Resolve-Path 'qwen2.5-0.5b-instruct-q4_k_m.gguf').Path
Get-FileHash -Algorithm SHA256 -LiteralPath $modelFile
docker run -d --rm --name repomind-eval-local-model -p 127.0.0.1:58093:8080 --mount "type=bind,src=$modelFile,dst=/model.gguf,readonly" ghcr.io/ggml-org/llama.cpp@sha256:7751a6c0dc3d019c231d42678abbb0fac1a13a24c17c956109ed27533139f280 -m /model.gguf --host 0.0.0.0 --port 8080 --ctx-size 4096 --parallel 1 --threads 4
Invoke-RestMethod http://127.0.0.1:58093/health
mvn '-Dtest=LocalGenerationIT' test
docker stop repomind-eval-local-model
~~~

With the model running and `docker compose -f eval/compose.yml --profile queue up -d --wait` complete, use `mvn '-Dspring.profiles.active=eval' '-Dtest=*Test,*Tests,*IT' test` for the combined run. The generator sends only the evidence and question to the production prompt builder. It saves each answer, its finish reason, token counts and prompt hash to `target/local-generation.json`. An HTTP success here only establishes that a sample was captured. Grade a fresh answer against `golden/generation.json`, including unsupported claims and missing sections. Keep the existing baseline and its review intact. The score script rejects a saved review if its answer hash changes.

## Compare SQS with direct JVM execution

These opt-in tests use Java 21, Maven and the pinned LocalStack image in the Compose queue profile. Synthetic URLs, dummy credentials and a loopback endpoint keep the exercise local. The production ingestion path writes to the disposable PostgreSQL database. Cloning, embedding and S3 are controlled test dependencies. [Results and failure ledger](REPORT.md#sqs-and-direct-jvm-workers)

~~~powershell
docker compose -f eval/compose.yml --profile queue up -d --wait
mvn '-Dspring.profiles.active=eval' '-Dtest=QueueSpeedIT,QueueCrashIT,QueueRecoveryIT,QueueLongRunningIT,QueueFailureRetryIT' test
python eval/score_queue.py target/queue-speed.json
docker compose -f eval/compose.yml --profile queue down
~~~

Run `python eval/score_queue.py eval/results/queue-speed.json` to audit the saved speed scorecard without starting services. Each arm warms up with four jobs and runs three 12-job bursts. Compare equal worker counts across transports and compare one versus four within each transport. The crash test forks two disposable JVMs, one per transport, and deliberately stops each with exit code 73 after four partial database writes. Logs and input IDs for each child are under `target/queue-crash/`. The parent test and Docker services stay running. The crash harness accelerates visibility, lease and polling timers. The speed harness uses the application poll-delay, long-poll and visibility settings.

## Check recovery after a reload or network drop, and detect prompt drift

`ActivityRecoveryIT` needs the Compose PostgreSQL service. The two commands below need no model server.

~~~powershell
mvn '-Dspring.profiles.active=eval' '-Dtest=ActivityRecoveryIT,ActivityResilienceTest,ChatControllerDetachTest,PromptDriftTest' test
npm --prefix frontend test
~~~

`PromptDriftTest` rebuilds the 24 generation prompts and compares their hashes with `results/generation-after.json`. If it fails, the saved 6/24 review no longer describes the current prompts and the local generation run must be repeated. Compare the hashes in `results/final-verification.json` with the working tree to see which benchmarked source files changed after the last recorded run. [Results](REPORT.md#failure-visibility-and-recovery-after-a-reload-or-network-drop)

## Check embedding retries and rate limits

`EmbeddingProviderIT` needs the Compose database; it drives the real Spring AI Mistral client against a loopback fake provider that answers scripted statuses, and proves how 429, 5xx, 401 and 400 are classified and how many HTTP requests each costs. `EmbeddingRetryPolicyTest` needs nothing. `EmbeddingRateLimitBenchIT` compares the original, previous and current retry behaviours against a simulated rate-limited provider (about two minutes) and writes `target/embedding-retry-bench.json`.

~~~powershell
docker compose -f eval/compose.yml up -d --wait postgres redis
mvn '-Dspring.profiles.active=eval' '-Dtest=EmbeddingRetryPolicyTest,EmbeddingProviderIT,EmbeddingRateLimitBenchIT' test
docker compose -f eval/compose.yml down
~~~

The benchmark provider limits, latency and job size are assumptions, not measurements of a real provider. [Results](REPORT.md#embedding-retries-and-provider-rate-limits)

## Run the second-repository search check

The second set (InsightStox) needs the Compose database only. It is report-only: it has no pass or fail floor, so nobody can tune the search against it.

~~~powershell
docker compose -f eval/compose.yml up -d --wait postgres redis
mvn '-Dspring.profiles.active=eval' '-Deval.set=insightstox' '-Dtest=RetrievalReplayIT' test
python eval/score.py --retrieval target/retrieval-insightstox-replay.json --retrieval-only
docker compose -f eval/compose.yml down
~~~

To build a new set, create `eval/golden/<name>/retrieval.tsv` (columns id, question, file as a path inside the snapshot, start, end, reference_fact; ids `ID01..`, `NL01..` and `DEV1..`). Write the labels and have them reviewed before the first search run. Then run `python eval/freeze_embeddings.py MODEL_DIR --set <name> --source-dir SNAPSHOT_DIR`, which also writes `sources.sha256.json`. Keep a `REVISIONS.md` of every label change and its date.

## Inspect or regenerate the retrieval fixture

The replay contains source excerpts, questions and locally computed vectors from commit `7988a6d76bc4e82169645eabc8e15bf05f37defb`. Inspect the compressed JSON with Python's gzip module. To generate a new copy, install NumPy, ONNX Runtime and Hugging Face tokenizers. Supply `tokenizer.json` and the quantized ONNX weights as `model.onnx` from the pinned [MiniLM release](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/tree/1110a243fdf4706b3f48f1d95db1a4f5529b4d41). Run `python eval/freeze_embeddings.py PATH_TO_MODEL_FILES`. The fixture records the weights checksum. The encoder truncates to 256 tokens, mean-pools using the attention mask and normalizes. The SQL replay pads the 384-dimensional vectors to the database's 1,024 dimensions with zeros, which preserves cosine distance. Historical source windows remain fixed so search and chunking changes can be measured independently.

## Apply schema changes to an existing installation

Apply `migrations/20261117_add_conversation_summary_and_sources.sql` , `migrations/20261117_add_identifiers_index.sql` and `migrations/20261117_add_ingestion_owner.sql` to the intended database before deploying the application changes. New installs receive these through `init.sql`. Historical comma-separated source lists still load. A filename that contained a comma in an old row cannot be reconstructed unambiguously from that field alone.


Drain old ingestion workers before deploying token-based ownership. The new migration was tested against the disposable evaluation database only. The long-running test uses a 2-second lease and 1-second visibility to force renewal during a blocked clone. See REPORT.md for failure evidence and limits.

For the retry follow-up alone, run `mvn -o '-Dspring.profiles.active=eval' '-Dtest=QueueFailureRetryIT,QueueRecoveryIT,QueueLongRunningIT,IngestionRetryTest,IngestionVisibilityTest' test` after starting the local services. PostgreSQL failure triggers are scoped to test UUIDs and removed in teardown; never run this profile against a real application database.
