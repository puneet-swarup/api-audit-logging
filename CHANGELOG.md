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