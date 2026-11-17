# Phase 0 Benchmark Harness Report

## Scope

Phase 0 created and validated the benchmark harness without changing application
production code. The harness is intended to make later baseline and
after-phase measurements reproducible.

## Components

- `docker-compose.bench.yml`: PostgreSQL with pgvector, Redis, LocalStack, and
  the configurable AI stub.
- `ai-stub/`: deterministic OpenAI-compatible embedding and chat responses with
  configurable latency, rate limits, and seeded failures.
- `generate_repo.py`: seeded synthetic repository generator with Git history.
- `apply-db.ps1`: applies `init.sql` and ordered SQL migrations to a clean
  benchmark database.
- `run-trivial.py`: records machine metadata and five measured runs.
- `compare.py`: compares median measurements between result files.
- `results.schema.json`: common result-file shape.

## Validation

The Docker Compose configuration was validated and the following services were
reachable:

- PostgreSQL/pgvector on port 55432
- Redis on port 56379
- LocalStack on port 4566
- AI stub on port 58080

The AI stub returned a 1024-element embedding for a test request.

The trivial benchmark result is recorded in
[`results/phase0-trivial.json`](results/phase0-trivial.json). It contains five
measured runs, the fixed seed, the commit hash, and the machine metadata. No
benchmark value is estimated in this report.

## Verified project notes

- The project uses Spring Boot 4.0.6; the README badge says Spring Boot 3.x.
- The database column is `vector(1024)`; older comments describe 768-dimensional
  embeddings.
- The rate limiter implementation is in-memory even though the README describes
  Redis-backed distributed limiting.
- The AWS setup policy does not currently list `s3:DeleteObject` or
  `sqs:ChangeMessageVisibility`.
- The existing backend test run had 12 passing tests and one application-context
  error because no datasource was configured in the environment.
- Frontend lint was not run because `frontend/node_modules` is absent.

These notes are observations for the baseline report; they are not Phase 0
fixes.
