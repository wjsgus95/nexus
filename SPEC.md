# Project Spec: CI Smoke, Regression & Kafka Traffic-Replay Diff CLI

## 1. Overview & Runtime Constraints
We are building a headless CLI tool packaged into a Docker image to run inside a CI runner.
- **Target Implementation Stack**: Java 21 with Gradle (Groovy DSL).
- **Target APIs**: 3 read-only HTTP/REST (JSON) microservices, each deployed across multiple regions (e.g., `KR`, `TW`).
- **Network Constraint**: Developer laptops cannot reach Target, Stable, HashiCorp Vault, or Kafka. Only the CI executor has network access to these internal services.
  - Because the feedback loop to real infrastructure requires a Docker build and CI run, the tool must support:
    1. **Offline local verification** (schema/config validation and mock-transport E2E tests that run on a laptop with zero network access).
    2. **Live CI pre-flight diagnostics (`doctor`)** to test each infrastructure slice independently in CI logs.

## 2. Configuration & Profile Organization
- **No Duplication**: Each `(service, region)` target (e.g., `catalog/kr`, `catalog/tw`) has:
  - Target URLs: `targetUrl` and `stableUrl` (Production).
  - Shared response diffing rules (e.g., ignored JSON paths/fields like `request_id` or `timestamp`, array order sensitivity) defined **once** per target—not repeated in every test file.
  - Kafka replay settings (`topic`, `max_messages`, `timeout`).
- **Test Case Files**:
  - Engineers can organize test cases across one or more files per `(service, region)` for `smoke` and `regression`.
  - Test cases support both `GET` (with query parameters) and `POST` (with inline/raw JSON request bodies) and region-specific parameters (e.g., Korean queries in `KR`, English/Chinese in `TW`).
- **Infra / Runner Settings**:
  - Loaded via environment variables (Vault address/path, Kafka bootstrap servers, concurrency limit, log level, report output directory).

## 3. Authentication
- **Shared Credentials**: Target and Stable (Production) accept the same auth headers.
- **Flow**: Fetch credentials from **HashiCorp Vault** (with environment-variable fallback for local/mock runs) -> exchange for an **OAuth2 Bearer token** -> attach `Authorization: Bearer <token>` and a consumer token header (`X-Consumer-Token`) to all HTTP requests.
- **Extensibility**: Isolate authentication behind a clean interface so an internal company Vault/OAuth SDK can be swapped in easily.

## 4. Three Separate CI Execution Stages
Each stage must be invokable as a separate CLI command so the CI pipeline can report to an internal platform **which exact stage** (`smoke`, `regression`, or `diff`) passed or failed:

1. **Stage 1 — Smoke (`smoke`) [MVP]**:
   - Sends curated smoke test requests to **Target only** with bounded concurrency.
   - Asserts HTTP status code and expected JSON field values/expressions, with configurable retries on failure to avoid flaky CI runs.
2. **Stage 2 — Regression (`regression`) [MVP]**:
   - Sends curated regression test requests concurrently to **both Stable (Prod) and Target**.
   - Evaluates assertions AND diffs the Target JSON response against the Stable JSON response using the target's shared diff rules.
3. **Stage 3 — Kafka Traffic Replay (`diff`) [Phase 2]**:
   - Consumes a bounded batch of sampled requests (`max_messages` or until `timeout`) from the target's Kafka topic. (Kafka messages contain the sampled HTTP request only, not the response.)
   - Replays each request concurrently against **both Stable (Prod) and Target**, and diffs the JSON responses using the target's shared diff rules.
   - *Optional capability*: Support auto-noise detection (Twitter Diffy pattern) by hitting Stable twice and automatically ignoring fields that differ between the two Stable responses.

## 5. Pre-Flight & Slice Diagnostics (`doctor`)
Provide a `doctor` command to debug issues step-by-step without running the full pipeline:
- `--offline` mode (runs on laptop and during `docker build`): Validates all config files, test case files, inline JSON payloads, and assertion query syntax with zero network calls.
- Live CI checklist mode: Checks (1) Config/Test files, (2) Vault + OAuth2 token exchange, (3) Target & Stable health reachability, and (4) Kafka broker/topic reachability.
- Offline diff preview (`doctor diff <stable.json> <target.json>`): Runs the JSON diff engine and report generator against two local JSON files on a developer laptop to tune ignore rules offline.

## 6. P0 CI Report Artifacts
Every run (`smoke`, `regression`, `diff`) must exit with a non-zero code on failure and output structured artifacts stamped with the stage name:
1. `report.json`: Machine-readable summary (stage, pass/fail counts, latencies, and structured per-request JSON diffs).
2. `report.md`: Human-readable summary table + collapsible diffs for failed requests (for CI job summaries).
3. `junit.xml`: Standard JUnit XML report for CI test UI integration.
