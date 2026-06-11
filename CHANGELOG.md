### [2.2.0] - 2026-06-12

- Upgraded the supported baseline to Spring Boot 3.5.15 and Spring Cloud 2025.0.3
- Centralized build, BOM, Lombok, Spotless, SonarQube, and test dependency versions in `gradle.properties`
- Removed hardcoded Spring Cloud OpenFeign versions so the Spring Cloud BOM owns the compatible client stack
- Upgraded Lombok to 1.18.46, Spotless to 8.6.0, SonarQube Gradle plugin to 7.3.1.8318, and Awaitility to 4.3.0
- Added the BOM-aligned JUnit Platform launcher to every module's test runtime
- Added Maven Central-ready publishing wiring with signed manual Central deployments
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
