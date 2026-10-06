# API Audit Logging — Test Support

This module makes it easy to assert that your endpoints and clients produce the audit records you
expect, in a plain Spring Boot test, with no database and no polling.

## Add the dependency

    testImplementation "io.github.puneet-swarup:api-audit-logging-test:3.0.0"

## Use it

Annotate a `@SpringBootTest` (or a slice test) with `@EnableAuditLoggingTest`, inject
`CapturedAuditLogs`, and assert:

    @SpringBootTest
    @AutoConfigureMockMvc
    @EnableAuditLoggingTest
    class PaymentAuditTest {

      @Autowired MockMvc mockMvc;
      @Autowired CapturedAuditLogs auditLogs;

      @Test
      void capturesIncoming() throws Exception {
        mockMvc.perform(get("/api/v1/payments/42"));
        auditLogs.single().hasType("INCOMING").hasMethod("GET").hasStatus(200);
      }
    }

## What you get

`CapturedAuditLogs` listens on the Spring event bus and records every published `ApiLogEvent`. Its
API:

| Method | Purpose |
|---|---|
| `all()` | immutable snapshot of captured records |
| `count()` | number captured |
| `isEmpty()` | whether nothing was captured |
| `anyMatch(predicate)` | whether any record matches |
| `single()` | assertions for the one record (fails if not exactly one) |
| `first()` | assertions for the first record (fails if none) |
| `clear()` | reset between tests |

`AuditLogAssertions` (returned by `single()` / `first()`) chains fluent checks:

| Method | Asserts |
|---|---|
| `hasType(String)` | record type (e.g. INCOMING, OUTGOING) |
| `hasMethod(String)` | HTTP method |
| `hasStatus(int)` | HTTP status |
| `urlContains(String)` | URL fragment |
| `hasCorrelationId(String)` | correlation ID |
| `hasTag(key, value)` | a tag key/value pair |
| `requestBodyContains(String)` | request body fragment |
| `responseBodyContains(String)` | response body fragment |
| `requestBodyDoesNotContain(String)` | proves a secret was masked |
| `record()` | the raw record for custom assertions |

The assertions are dependency-free — they throw AssertionError and chain — so they work in JUnit,
TestNG, or plain Java.

## Tips

- Call `auditLogs.clear()` in `@BeforeEach` when a test class performs several exchanges.
- Combine with `api-audit-logging-storage-memory` when you also want to exercise the storage path.
- Disable auditing entirely with `audit.logging.enabled=false` for tests that do not care about it.
