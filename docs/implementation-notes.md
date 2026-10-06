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
