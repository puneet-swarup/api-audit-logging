# Roadmap

This file captures the next useful pieces of work for contributors. The library already supports
inbound Spring MVC capture, Feign, RestTemplate, RestClient, WebClient metadata, JPA, JDBC, memory,
and Kafka sinks. The items below are the next steps to make the project even stronger in production.

## Delivered

- Configuration-driven, zero-code-change path-based auditing (Phase 1). See the
  [Path-Based Auditing Guide](docs/path-based-auditing-guide.md).
- Pluggable `AuditPolicy` engine with `AuditDecision`, capture modes, and a custom-policy SPI.
- JSON-tree payload masking behind a `PayloadMasker` SPI (correct nested/array/typed masking).
- Hardened internal endpoint security (constant-time key compare, exact path match) and correlation-ID sanitization.
- Sampling, drop metrics, and backpressure visibility.
- Copy-safe record masking (`toBuilder`) and core dependency-direction guardrail.
- A comprehensive `api-audit-logging-examples` cookbook module covering every option and feature.
- Storage-agnostic retention SPI, Kafka dead-letter/retry, and GraalVM native-image hints.

## Near Term

- Add Testcontainers coverage for PostgreSQL, MySQL/MariaDB, SQL Server, and Oracle-compatible
  schema behavior.
- Add Kafka integration tests with an embedded broker or Testcontainers Kafka.
- Add a WebFlux inbound `WebFilter` for applications that do not run on Spring MVC.
- Expand WebClient capture once body replay can be handled safely without surprising reactive
  applications.

## Production Hardening

- Add CI matrix jobs for Java 21, supported Spring Boot versions, and the main storage profiles.
- Publish starter and module artifacts with complete Maven Central metadata.

## Contribution Notes

Small, focused pull requests are welcome. Tests should cover any new production behavior, and public
classes should keep the existing documentation style with clear Javadocs and the author tag.
