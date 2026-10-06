### [Unreleased]

- Added Kafka dead-letter and bounded retry: the sink observes the producer result, retries failed sends, and routes to a configurable dead-letter topic (`audit.logging.kafka.dead-letter-topic`, `retries`, `retry-backoff-ms`)
- Added the storage-agnostic retention SPI (`AuditRetentionPolicy`) and a scheduler that purges every retention-capable store (JPA, JDBC, memory, custom) on the configured cron; replaced the JPA-only cleanup

### [3.1.0] - 2026-10-07

- Added `schemaVersion` to `AuditLogRecord` (default 1) for downstream event contract evolution, persisted by JPA (V1003 migration for 5 vendors) and JDBC
- Added the `api-audit-logging-test` module: `@EnableAuditLoggingTest`, `CapturedAuditLogs`, and fluent `AuditLogAssertions` for asserting audit behavior in host tests
- Added the `api-audit-logging-storage-file` module: a JSON-lines file / stdout sink (`storage.type=file` or `stdout`)
- Added opt-in WebClient response-body capture (`audit.logging.webclient.capture-bodies`) that buffers and re-publishes the body so callers are unaffected; added `AuditBodyInserters` for request-body capture at the call site
- Added Testcontainers integration tests: PostgreSQL and MySQL migration tests, and a Kafka producer round-trip test (skipped automatically when Docker is unavailable)
- Fixed MySQL migrations: `V1000`/`V1001` no longer use the unsupported `ADD COLUMN IF NOT EXISTS` syntax and `V1002` uses a plain `ALTER TABLE`; added the Flyway 10+ database modules (`flyway-database-postgresql`, `flyway-mysql`) to the migration tests

### [3.0.0] - 2026-10-06

- **BREAKING:** Decoupled the storage search SPI from Spring Data. `AuditLogSearchStore` now takes a framework-neutral `AuditLogQuery` and returns `AuditLogPage`; HTTP pagination is handled at the web layer. Custom store implementations must migrate — see `docs/spi-migration-guide.md`
- Fixed a duplicate `.tags(...)` builder call in the JDBC store's row mapper

### [2.3.0] - 2026-10-06

- Replaced regex-based masking with a JSON-tree `PayloadMasker` SPI and default `JsonTreePayloadMasker` that correctly masks nested objects, arrays, and non-string values without corrupting payloads; `JsonMasker` is retained as a delegating facade
- Hardened the internal endpoint security filter with constant-time API-key comparison and exact/child path matching
- Sanitized inbound `X-Correlation-ID` values (control-character stripping, length clamp) to prevent log/MDC injection
- Added `tagKey`/`tagValue` filters to the internal audit search endpoint and all searchable stores (JPA, JDBC, memory)
- Added optional sampling (`audit.logging.sampling.*`) that stores a fraction of successful records while always capturing errors
- Added `AuditMetrics.recordDropped` and `api.audit.records.dropped` counter; rejection-policy drops and sampled-out records are now observable
- Made `AuditLogRecord` copy-safe (`toBuilder`) and refactored masking to never drop fields
- Added dependency-direction guardrail test and fail-fast `@Pattern` validation for `storage.type`
- Added the `api-audit-logging-examples` module: a runnable cookbook covering every option, feature, and integration with integration tests
- Added the Deployment Guide: packaging styles (fat JAR, WAR, plain JAR, Docker/Kubernetes), storage choice, configuration mechanisms, schema handling, security, and a verification checklist
- Added a pluggable audit policy engine (`AuditDecisionEngine`) that resolves one decision per request from one or more `AuditPolicy` sources
- Added configuration-driven, zero-code-change path-based auditing via `audit.logging.policies.path-based.rules`
- Added the `PathAuditPolicy` (Ant and regex matchers, method filters, specificity ranking, explicit skips) and the `AnnotationAuditPolicy` (preserves the existing `@AuditLog` flow)
- Added the `AuditPolicy` SPI and `AuditDecision`/`CaptureMode` model, plus `AuditPolicyOrder` constants for custom policies
- Added capture modes: `FULL`, `METADATA_ONLY`, and `BODY_ONLY`
- Added a `tags` map to `AuditLogRecord`, persisted as JSON by the JPA and JDBC stores, with new `V1002` migrations for H2, PostgreSQL, MySQL, SQL Server, and Oracle
- Added fail-fast JSR-380 validation to `AuditLoggingProperties`
- Added `AuditRequestAttributes` to centralize request attribute keys
- Added unit, auto-configuration, and end-to-end tests for path-based auditing, plus a `path-based` demo profile and an unannotated `PathAuditDemoController`
- Added the Path-Based Auditing Guide and expanded the README with rule examples and the new configuration reference
- Inbound capture now skips request/response wrapping entirely when no policy audits the request

### [2.2.0] - 2026-06-12

- Upgraded the supported baseline to Spring Boot 3.5.15 and Spring Cloud 2025.0.3
- Centralized build, BOM, Lombok, Spotless, SonarQube, and test dependency versions in `gradle.properties`
- Removed hardcoded Spring Cloud OpenFeign versions so the Spring Cloud BOM owns the compatible client stack
- Upgraded Lombok to 1.18.46, Spotless to 8.6.0, SonarQube Gradle plugin to 7.3.1.8318, and Awaitility to 4.3.0
- Added the BOM-aligned JUnit Platform launcher to every module's test runtime
- Added Maven Central-ready publishing wiring with signed manual Central deployments
- Changed published coordinates to the Maven Central-verifiable `io.github.puneet-swarup` groupId
- Refreshed README and Kafka consumer examples for the 2.2.0 dependency snippets

### [2.1.0] - 2026-06-02

- Added inbound path include/exclude controls with Ant-style patterns
- Added optional Micrometer audit metrics for saved records, failed records, and store duration
- Added no-op audit metrics fallback for applications without Micrometer
- Added tests for path capture controls, metrics auto-configuration, and listener failure metrics
- Added demo endpoints and integration tests for Feign, WebClient, RestClient, HTTP interfaces, and path-control behavior
- Expanded Spring Boot configuration metadata for path controls
- Added source and Javadoc jars to published library artifacts
- Refreshed README dependency snippets and production observability guidance for `2.1.0`

### [2.0.0] - 2026-05-25

- Reworked the project into a modular Spring Boot starter layout
- Added common auto-configuration module separate from client and storage integrations
- Moved Feign configuration into `api-audit-logging-client-feign`
- Added Feign logger coverage for annotated outbound capture and response re-buffering
- Added Feign error decoder coverage for masked non-2xx response capture and delegation
- Added RestTemplate outbound audit logging with response re-buffering
- Added RestClient outbound audit logging using the same blocking-client interceptor
- Added blocking-client coverage for outbound event capture, correlation propagation, and response buffering
- Added blocking-client coverage for non-2xx outbound responses
- Added WebClient outbound metadata capture and correlation propagation
- Added WebClient coverage for disabled auto-configuration and non-2xx responses
- Added outbound transport failure auditing as `OUTGOING_TRANSPORT_ERROR`
- Added focused WebClient auto-configuration coverage for event publishing and correlation
- Added capture support for query strings, redacted headers, client IP, user agent, and principal name
- Added tests for metadata masking, client capture paths, JDBC persistence, JPA mapping, and demo API retrieval
- Added configurable body and header capture limits with truncation markers
- Added structured error metadata for inbound failures and Feign error responses
- Added JPA storage as a selectable module with Boot auto-configuration imports
- Added JDBC storage for database-backed auditing without JPA
- Added JDBC storage coverage for save/search behavior against an in-memory database
- Added auto-configuration coverage for no-store safety and storage selection behavior
- Changed direct storage modules to require explicit `audit.logging.storage.type`; starter keeps the JPA default
- Expanded internal search filters across JPA, JDBC, and memory stores
- Added in-memory storage for demos and integration tests
- Added demo end-to-end coverage for JDBC and memory storage profiles
- Added demo multi-hop correlation test for inbound plus outbound audit records
- Added Kafka audit sink as an opt-in streaming backend
- Added Kafka auto-configuration coverage for opt-in behavior and custom topic publishing
- Added Kafka/SIEM consumer guidance for downstream audit platforms
- Added storage selection property and demo profiles for JPA, JDBC, memory, and Kafka sinks
- Added multi-service correlation guide with cross-service trace lookup examples
- Added internal endpoint security guide and blank API-key coverage
- Added Spring Boot configuration metadata for `audit.logging.*` properties
- Added HTTP interface client coverage for proxies backed by audited RestClient builders
- Moved audit schema migration into storage modules under `db/audit-migrations`
- Split audit schema migrations by database vendor for H2, PostgreSQL, MySQL/MariaDB, SQL Server, and Oracle
- Added additive metadata-column upgrade migrations for existing audit tables
- Fixed audit logging defaults so `audit.logging.enabled` is truly enabled unless set to false
- Fixed POST/PUT inbound request body auditing when the servlet request is wrapped
- Expanded demo integration coverage for POST body capture
- Refreshed README for module selection, storage choices, and client integrations

### [1.1.0] - 2026-04-21

- Introduced `AuditLoggingProperties` POJO replacing `@Value` annotations
- Configurable async rejection policy (`CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD` / `ABORT`)
- Secured `/internal/audit-logs` with configurable API key filter
- Configurable sensitive field masking list (`audit.logging.masking.additional-fields`)
- Flyway migration made opt-in (`audit.logging.flyway.enabled`, default: false)
- Configurable cleanup cron expression (`audit.logging.cleanup.cron`)
- Fixed streaming response body capture (SSE no longer buffered)
- Switched to `java-library` plugin for correct transitive dependency exposure
- Removed Shadow plugin (inappropriate for library artifacts)
- GitHub Packages publishing with full POM metadata
- Upgraded Spotless to 7.0.2 for Gradle 9 compatibility
- Fixed test compilation: constructor alignment, missing beans, missing dependencies

### [1.0.0] - 2026-04-15

Initial release of API Audit and Logging Library.
Support for Inbound HTTP and Outbound Feign interception.
