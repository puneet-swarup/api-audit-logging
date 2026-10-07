# CONTEXT_TEMP.md — Working Context & Decisions (Phase 0 + Phase 1)

> Persistent scratchpad to prevent drift/hallucination across agent turns.
> Read this file when in doubt. If a decision is not here, ASK the user.

## User-confirmed decisions (2026-10-06)

1. **Precedence** — CONFIRMED as proposed:
   - Most-specific rule wins.
   - Annotation beats a generic path match.
   - Explicit `audit: false` always wins (highest priority).
2. **Description/tags without annotation** — CONFIRMED: take from matching config rule; otherwise fall back to `"<METHOD> <path>"`.
3. **Rule syntax** — CONFIRMED: Ant patterns by default, optional `methods:` and `capture:` per rule; regex available as an additional matcher type. **Must provide examples in BOTH documentation AND README.**
4. **Phase 1 scope** — CONFIRMED: inbound Spring MVC only. WebFlux/clients come later (Phase 6).
5. **Naming** — CONFIRMED: `audit.logging.policies.path-based.rules[*]`. User reasoning: it is specific and self-describing (signals path-based vs annotation-based). The generic `audit.logging.paths[*]` does NOT convey which approach is in use. **Keep `audit.logging.policies.path-based.rules[*]`.**

## Additional user directive
- Keep compacting context; write it to CONTEXT_TEMP.md so there is no drift.
- If a decision is not recorded here, ASK the user instead of assuming.

## Scope being implemented NOW
- **Phase 0 — Foundations**
  - JSR-380 `@Validated` constraints on `AuditLoggingProperties` (fail-fast config).
  - Extract magic request-attribute strings into a constants holder (`AuditRequestAttributes`).
  - Dependency-direction / guardrail test.
- **Phase 1 — Annotation-free Path Auditing (headline feature)**
  - `AuditDecision` immutable value object: `audit`, `description`, `tags`, `captureMode`.
  - `AuditPolicy` SPI.
  - `AnnotationAuditPolicy` (wraps current `@AuditLog` behavior).
  - `PathAuditPolicy` (config-driven rules: Ant pattern, methods, capture mode, audit true/false, description, tags, optional regex matcher type).
  - `AuditDecisionEngine` — deterministic resolution per confirmed precedence.
  - Refactor `IncomingLoggingFilter` to consult engine BEFORE wrapping; skip wrapping when not audited.
  - Config block `audit.logging.policies.*` + Spring config metadata.
  - Full Javadoc, README + docs examples, and complete test suite.

## Design notes / invariants
- Backward compatible: `@AuditLog` still works. `policies.annotation.enabled` default = true.
- Path-based auditing is opt-in: `policies.path-based.enabled` default = false.
- A request is published at most ONCE per request (engine returns a single decision).
- Explicit skip rule (`audit: false`) wins over everything, including annotation.
- Non-audited requests must NOT allocate `ContentCachingResponseWrapper` / `EagerRequestWrapper`.
- Capture modes (Phase 2 will complete, but model them now): FULL, METADATA_ONLY, BODY_ONLY.
  - Phase 1 default = FULL.

## Known codebase facts (verified by reading)
- `IncomingLoggingFilter` gates publishing on request attribute `AUDIT_LOG_ENABLED`.
- `AuditLogInterceptor.preHandle` reads `@AuditLog` (method, then class) and sets `AUDIT_LOG_ENABLED` + `AUDIT_LOG_DESC`.
- `LoggingAutoConfiguration` is `@ConditionalOnProperty(audit.logging.enabled, matchIfMissing=true)`.
- `AuditLogRecord` is an immutable Lombok `@Value @Builder`.
- Masking happens in `ApiLogListener` (async), NOT in the filter — keep it that way.
- Core depends on spring-boot-starter-web, spring-boot-starter, spring-data-commons.
- Tests use JUnit 5 + AssertJ + Mockito + ApplicationContextRunner + ReflectionTestUtils.
- Java 21, Spring Boot 3.5, Lombok, google-java-format via Spotless (compileJava depends on spotlessApply).

## Open questions (ask user if encountered)
- (none currently)

## Changelog of this file
- 2026-10-06: created; recorded all 5 decisions + scope for Phase 0/1.

## Implementation progress log
- 2026-10-06: Phase 0 + Phase 1 IMPLEMENTED and BUILD GREEN (gradlew build -x javadoc).
  - New: AuditRequestAttributes, CaptureMode, AuditDecision, AuditPolicy, AuditPolicyOrder,
    PathMatcherType, AnnotationAuditPolicy, PathAuditPolicy, AuditDecisionEngine.
  - Properties: policies.annotation.enabled, policies.path-based.{enabled,rules[*]} with JSR-380 validation.
  - AuditLogRecord gained a `tags` map; JPA/JDBC stores persist it as a JSON string; new V1002 migrations (all 5 vendors).
  - IncomingLoggingFilter now consults AuditDecisionEngine (two-phase: pre-chain for wrapping/skip, post-chain for annotation).
  - Tests added: PathAuditPolicyTest, AuditDecisionEngineTest, PathBasedAuditingAutoConfigurationTest,
    PathBasedAuditingIntegrationTest (demo), plus updates to IncomingLoggingFilterTest and JdbcAuditLogStoreTest.
  - Demo: PathAuditDemoController (NO annotation) + application-path-based.yaml profile.
- REMAINING: README + docs examples for path-based config (user explicitly requested examples in both).

## FINAL STATUS (2026-10-06)
- Phase 0 + Phase 1 COMPLETE. Full build GREEN: gradlew build -x javadoc.
- Docs done: docs/path-based-auditing-guide.md created; README updated (section + config table); CHANGELOG + ROADMAP updated.
- All 5 user decisions honored. Naming kept as audit.logging.policies.path-based.rules[*].
- Examples provided in BOTH README and the docs guide (user requirement #3 satisfied).
- Verified test counts: core + autoconfigure green; demo app (incl. PathBasedAuditingIntegrationTest) green; jdbc/jpa storage green.
- CONTEXT_TEMP.md retained intentionally for future phases (Phase 2-7 still pending: WebFlux, WebClient body capture, JSON-tree masker, sampling, module hygiene).

## IMPORTANT FIX + DEMO ENRICHMENT (2026-10-06, pre-commit)
- BUG FOUND & FIXED: `ApiLogListener.mask()` rebuilt the AuditLogRecord field-by-field and DROPPED the
  new `tags` field. This would have silently lost tags in production for every storage backend.
  Fixed by copying `.tags(original.getTags())`. Discovered precisely BECAUSE the demo integration
  test asserted tags end-to-end (user's insistence on samples paid off).
- Demo enriched to cover ALL Phase 1 features:
  - application-path-based.yaml now shows: Ant rule + description + tags + FULL; METADATA_ONLY;
    BODY_ONLY; method-scoped rule; REGEX matcher rule; explicit skip rule.
  - PathAuditDemoController gained /metadata/ping, /body-only/echo, /mutations (POST/GET/PUT),
    /orders/{id} to exercise each rule.
  - PathBasedAuditingIntegrationTest expanded to verify description, tags, METADATA_ONLY (no body),
    BODY_ONLY (no headers), method scoping, and skip.
  - New api-audit-demo-app/README.md documents every profile, endpoint, integration steps.
  - Main README demo section updated with path-based profile + endpoints + demo README link.
- Full build GREEN: gradlew build -x javadoc.
- NOTE for future: any new AuditLogRecord field MUST be added to ApiLogListener.mask() AND to all
  storage stores (JPA/JDBC/memory) + outbound client record builders. This is a known fragility;
  Phase 5 module hygiene should consider a record-level copy/mask API to prevent this class of bug.

## PHASE 3 — Correctness & Security (2026-10-06)
- Replaced the regex-based masking with a JSON-tree masker:
  - New SPI: `com.api.audit.mask.PayloadMasker`.
  - New default impl: `com.api.audit.mask.JsonTreePayloadMasker` — walks the parsed JSON tree,
    masks sensitive keys at any depth (objects AND arrays), masks any value type (string/number/
    boolean/null/object), handles escaped quotes and complex values without corruption, and leaves
    non-JSON payloads untouched (never throws). Configurable max depth (default 64).
  - `JsonMasker` is now a thin backward-compatible facade delegating to the active `PayloadMasker`.
  - Auto-config registers `PayloadMasker` with `@ConditionalOnMissingBean`, so a user bean wins.
- Security filter hardening (`AuditLogSecurityFilter`):
  - Constant-time API-key comparison via `MessageDigest.isEqual` (no timing side-channel).
  - Exact/child path matching (`/internal/audit-logs` or `/internal/audit-logs/...`), so
    `/internal/audit-logs-evil` is NOT treated as protected.
- Correlation-ID sanitization (`IncomingLoggingFilter.sanitizeCorrelationId`): strips control
  chars/newlines/angle brackets, clamps to 128 chars, discards blank — prevents log/MDC injection
  and oversized records. Applied to the inbound `X-Correlation-ID` header.
- Tests added: `JsonTreePayloadMaskerTest` (nested/array/types/escapes/non-JSON/malformed),
  security filter lookalike + child-path tests, correlation sanitization tests.
- Full build GREEN.

## PHASE 2 — Capture Modes & Metadata (2026-10-06)
- Tag filtering on the search surface:
  - `AuditLogSearchStore.search(...)` gained `tagKey` and `tagValue` params.
  - `ApiLogController` exposes `?tagKey=...&tagValue=...`.
  - JPA (`ApiLogSpecifications.addTagPredicate`), JDBC (`buildWhereClause`), and memory
    (`matchesTags`) all honor the same contract: tagKey alone = any record carrying the key;
    tagKey+tagValue = exact pair. Tags are stored as JSON text, so JPA/JDBC use LIKE on the
    serialized `"key":"value"` pair; memory compares the parsed map directly.
- Capture modes documented for downstream consumers in `docs/kafka-siem-consumer-guide.md`
  (FULL/METADATA_ONLY/BODY_ONLY effects on the event, plus tags for routing). Noted that outbound
  client capture is NOT policy-governed and always captures full records.
- README: filter list + HTTP example updated with tag filters; config reference already lists policies.
- Tests: memory tag-filter test (key, key+value, wrong value), JDBC tags round-trip + tag filter
  params, JPA spec tag predicate assertion, demo tag-filter integration test. Controller/search
  service/spec tests updated for the new signature.
- Full build GREEN.

## PHASE 4 — Sampling, Metrics & Backpressure (2026-10-06)
- Sampling:
  - New config block `audit.logging.sampling.{enabled,sample-rate,always-capture-errors}`
    (JSR-380 validated: sample-rate in [0.0, 1.0]).
  - `SamplingStrategy` (core, policy package): random keep at sample-rate for successes; error
    records (type ends `_ERROR` or httpStatus >= 500) always kept when always-capture-errors=true.
    Defensive clamping + kept/dropped counters.
  - Applied in `IncomingLoggingFilter.processAuditCapture` (post-assembly, where status is known).
    Sampled-out records call `AuditMetrics.recordDropped("SAMPLED", record)` and are not published.
  - Sampling is disabled by default (rate 1.0) — no behavior change unless configured.
- Metrics:
  - `AuditMetrics` gained `recordDropped(String reason, AuditLogRecord record)`.
  - NoOp + Micrometer impls updated; Micrometer emits `api.audit.records.dropped{reason,type}`.
- Backpressure:
  - `logExecutor` now takes `AuditMetrics`; DISCARD_OLDEST and DISCARD rejection handlers call
    `recordDropped("QUEUE_FULL", null)` in addition to the existing WARN log.
- Tests: `SamplingStrategyTest` (disabled/full/zero/error-always/clamped/approximate-rate),
  filter sampling-drop test, autoconfigure sampling-bean + drop-counter tests.
- README: new "Sampling and Metrics" section + config rows. CHANGELOG updated.
- Full build GREEN.
- NOTE: `patch_file` leaves a `.orig` backup beside the patched file — always delete it after.

## PHASE 5 — Module Hygiene & Robustness (2026-10-06)
- Structural fix for the field-drift fragility:
  - `AuditLogRecord` is now `@Builder(toBuilder = true)`.
  - `ApiLogListener.mask()` uses `original.toBuilder()` and overrides ONLY the two body fields, so
    every present and future field is carried over automatically. This closes the bug class that
    caused `tags` to be dropped.
  - New `ApiLogListenerTest` pins the guarantee (all fields preserved; bodies redacted).
- Dependency-direction guardrail: new `DependencyDirectionTest` fails if the core imports any
  storage/client module package. Dependency-free (no ArchUnit).
- Storage type validation: `audit.logging.storage.type` now carries a JSR-380 `@Pattern`
  (jpa|jdbc|memory|kafka) for fail-fast configuration.
- DELIBERATELY DEFERRED (documented, not done):
  - Splitting the `AuditLoggingProperties` god-object into per-module property classes. High blast
    radius (every module reads it) and low user-visible value; can be done incrementally later.
  - Framework-neutral `AuditLogPage` to remove spring-data-commons from the SPI. Breaking SPI change;
    needs a compatibility shim. Deferred to avoid destabilizing storage modules.
  - Enum-based storage selection: kept as a validated String because it is referenced inside
    `@ConditionalOnExpression` string literals across modules; converting risks breaking conditional
    wiring for little gain.
- Full build GREEN.

## EXAMPLES MODULE — Comprehensive Cookbook (2026-10-06)
- New module `api-audit-logging-examples` (not published; bootJar only).
- Every feature has a named example class (SOLID single-responsibility) under a feature package:
  annotation, pathbased, masking, policy, clients.
- Profiles: pathbased, masking, policy, observability, memory (each with an application-*.yaml).
- Cookbook README maps every option -> class -> endpoint -> observable effect.
- Integration tests prove each documented effect on stored records:
  PathBasedRulesExampleTest, MaskingExampleTest, HeaderBasedAuditPolicyTest.
- Client examples: Feign, RestTemplate, RestClient, WebClient (ClientConfiguration exposes the
  Spring-managed clients so the library customizers apply).
- Full build GREEN (all modules incl. examples).

## DEPLOYMENT GUIDE (2026-10-06)
- Added docs/deployment-guide.md: comprehensive, copy-paste deployment reference.
  - Defaults table (what you get out of the box).
  - Packaging styles: fat JAR (embedded Tomcat/Jetty), WAR on external container, plain JAR (non-web),
    Docker/Kubernetes. Each with exact steps and what NOT to do.
  - Dependency choices: starter vs individual modules (with module reference table).
  - Storage choices: JPA, JDBC, memory, Kafka.
  - Every configuration mechanism: bundled YAML, external YAML, env vars, CLI flags, profiles.
  - Schema: Flyway (library migration) vs own schema vs ddl-auto.
  - Observability/metrics, security, verification checklist, troubleshooting table, deployment matrix.
- README links the Deployment Guide.
- No code changes; docs only.

## CORE DECOUPLING — Framework-neutral search SPI (2026-10-06)
- The storage SPI no longer depends on Spring Data.
  - New `com.api.audit.query.AuditLogQuery` (immutable value object: all filters + page/size/sort).
  - New `com.api.audit.query.AuditLogPage<T>` (content + page/size/total, with `map`, `empty`).
  - `AuditLogSearchStore` now exposes `AuditLogPage<AuditLogRecord> search(AuditLogQuery query)`.
- `ApiLogController` is the only Spring-Data-aware layer: it accepts a `Pageable`, converts to
  `AuditLogQuery` (handling `Pageable.unpaged()` safely), and returns `AuditLogPage`. Jackson
  serializes `AuditLogPage` directly.
- JPA store builds `Pageable` internally and converts Spring Data `Page` -> `AuditLogPage` at the
  boundary; Spring Data stays inside the JPA module.
- JDBC and memory stores implement the neutral contract directly; JDBC now also honors `sortBy`
  (whitelisted columns) and sort direction.
- Fixed a real pre-existing bug: the JDBC row mapper called `.tags(...)` five times (copy-paste).
- Tests updated across core, autoconfigure, JPA, JDBC, memory, examples, and demo; added pagination/
  sorting and filter-mapping tests. Full build GREEN.
- BREAKING: this is an SPI signature change. Custom `AuditLogSearchStore` implementations must move
  from the old parameter list + Spring Data `Page` to `search(AuditLogQuery) -> AuditLogPage`. This is
  a 3.0.0-class change; the 2.3.0 entry documents it while the project is still pre-3.0.
- DEFERRED (documented): splitting the god-object `AuditLoggingProperties`; it is high blast radius
  with low user-visible value and can be done incrementally.

## PHASE 6 (Part A + B) — WebClient body capture + Testcontainers (2026-10-07)
- WebClient response-body capture (opt-in `audit.logging.webclient.capture-bodies`):
  - New `WebClientBodyCapture` (join buffers, size-limited toCapturedString) — single responsibility.
  - New `WebClientAuditFilter` — builds the ExchangeFilterFunction; response body drained, captured,
    and rebuilt so downstream callers still receive it.
  - New `AuditBodyInserters.fromValue(value, holder)` for request-body capture at the call site
    (ClientRequest bodies are opaque BodyInserters and cannot be read by a filter).
  - New property block `audit.logging.webclient.capture-bodies` (default false).
  - Tests: `WebClientBodyCaptureTest` (unit), `WebClientAuditFilterTest` (MockWebServer; proves body
    capture preserves the downstream body and captures transport errors).
- Testcontainers integration tests (real engines), auto-skipped without Docker:
  - `AuditLogMigrationPostgresIT`, `AuditLogMigrationMysqlIT` — run the library's Flyway migrations
    and assert the resulting schema (incl. the `tags` column).
  - `KafkaAuditLogStoreIT` — a record round-trips through a real broker, keyed by correlation ID.
  - Added `testcontainersVersion` to gradle.properties; test deps added to storage-jpa and storage-kafka.
- Examples: new `WebClientBodyCaptureExample` + `webclient-bodies` profile; examples README updated.
- Docs: README WebClient section + config row; deployment guide sections for Testcontainers and
  WebClient body capture.
- WebFlux inbound filter (Part C) NOT done — it requires a framework-neutral AuditRequest abstraction
  (a breaking change) and is deferred to 4.0.0 per the agreed plan.
- Full build GREEN.

## FIX — Real-DB migration tests + MySQL migration SQL (2026-10-07)
- Root cause of the CI failures in AuditLogMigrationPostgresIT / AuditLogMigrationMysqlIT:
  1. Flyway 10+ split database support into separate modules; only flyway-core was present, so
     Flyway reported an unsupported database. Added testRuntimeOnly flyway-database-postgresql and
     flyway-mysql.
  2. MySQL migrations used `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`, which MySQL does not support,
     and repeated columns already created by V999. V1000/V1001 are now safe no-op `SELECT 1;` for
     version parity; V1002 is a plain `ALTER TABLE ... ADD COLUMN tags LONGTEXT`.
- H2 (the default/test DB) was never affected, which is why local tests passed and only real-DB CI
  failed.
- Documented the Flyway 10+ module requirement in README and the deployment guide, since it affects
  any host using the library's Flyway path.

## TIER 3 — Adoption multipliers (2026-10-07)
- Schema versioning: `AuditLogRecord.schemaVersion` (default 1, CURRENT_SCHEMA_VERSION). Persisted by
  JPA (V1003 migration for h2/postgresql/mysql/sqlserver/oracle) and JDBC (insert + row mapper).
  Carried through masking automatically via toBuilder.
- Testing support module `api-audit-logging-test`:
  - `@EnableAuditLoggingTest` (registers the capture bean) + `CapturedAuditLogs` (Spring event
    listener collecting ApiLogEvent records) + `AuditLogAssertions` (dependency-free fluent checks).
  - Own README; self-tested by CapturedAuditLogsTest.
- File/stdout sink module `api-audit-logging-storage-file`:
  - `JsonLineAuditLogStore` writes one JSON object per line to a file or stdout; registers JavaTime
    module. Auto-config activates on storage.type=file|stdout; `audit.logging.file.path` config.
  - Extended storage.type @Pattern to allow file|stdout. Own README-worthy Javadoc; tested.
- README: testing section, storage module table, file/stdout storage choice, config row.
- Full build GREEN.

## PHASE 7 — Batch 1: storage-agnostic retention SPI (2026-10-07)
- New `com.api.audit.spi.AuditRetentionPolicy` (core): `long purgeBefore(LocalDateTime cutoff)`.
- New `AuditRetentionScheduler` (autoconfigure): runs on `audit.logging.cleanup.cron`, invokes every
  AuditRetentionPolicy bean, isolates failures, logs totals. Bean is `@ConditionalOnProperty`
  cleanup.enabled=true.
- JPA/JDBC/memory stores now implement AuditRetentionPolicy. JPA repository delete now returns count.
- Removed the JPA-only `CleanupConfig` and its test; superseded by the scheduler. Added
  JpaAuditLogStoreRetentionTest, InMemoryAuditLogRetentionTest, AuditRetentionSchedulerTest.
- Docs: README config row updated; deployment guide retention section.
- Full build GREEN.

## PHASE 7 — Batch 2: Kafka DLQ + retry (2026-10-07)
- KafkaAuditLogStore now observes the producer result (whenComplete), retries failed sends with a
  backoff, and routes to a dead-letter topic when configured; never throws to the audit thread.
- New config: kafka.dead-letter-topic (optional), kafka.retries (default 2), kafka.retry-backoff-ms
  (default 500), with @Min validation.
- Tests: KafkaAuditLogStoreTest (success, retry+DLQ via mocked template); existing
  KafkaAuditLogAutoConfigurationTest updated to stub the producer future.
- Docs: README config rows; Kafka guide reliability section.
- Full build GREEN.

## PHASE 7 — Batch 3: GraalVM native hints (2026-10-07)
- New AuditLogRuntimeHints (core) registers reflection for AuditLogRecord, AuditLogQuery,
  AuditLogPage; registered via META-INF/spring/aot.factories.
- Test: AuditLogRuntimeHintsTest asserts the type hints are registered.
- Docs: deployment guide native-image section. Production code uses no direct reflection, so Spring
  AOT plus these hints cover the library.
- Full build GREEN.
- PHASE 7 COMPLETE (retention SPI, Kafka DLQ/retry, native hints).

## 4.0.0 — Framework-neutral AuditRequest + WebFlux (2026-10-07)
- New `com.api.audit.spi.AuditRequest` (path, method, header, attribute get/set) and
  `ServletAuditRequest` adapter.
- AuditPolicy.decide and AuditDecisionEngine.decide now take AuditRequest (BREAKING for custom
  policies). Updated AnnotationAuditPolicy, PathAuditPolicy, IncomingLoggingFilter (wraps req),
  tests, and the examples custom policy.
- New module api-audit-logging-webflux: ReactiveAuditRequest (over ServerWebExchange) +
  ReactiveIncomingAuditFilter (WebFilter) + WebFluxAuditAutoConfiguration. Correlation carried in
  reactor Context and echoed on the response header. Reuses engine/policies/sampling/metrics.
- Tests: ReactiveIncomingAuditFilterTest (match, non-match, correlation propagation).
- Docs: spi-migration-guide 4.0.0 section; README WebFlux section + module row; CHANGELOG split into
  3.2.0 (Phase 7, non-breaking) and 4.0.0 (breaking AuditRequest + WebFlux). Version bumped to 4.0.0.
- Full build GREEN.
