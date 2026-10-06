# Deployment Guide

This guide is a complete, copy-paste reference for integrating and deploying the API Audit Logging
library. It covers every packaging style, every storage option, every way to supply configuration,
and the defaults you get if you change nothing.

If you read only one section, read [Defaults](#defaults-everything-out-of-the-box) and
[Choose your packaging style](#choose-your-packaging-style).

## Contents

- Defaults: everything out of the box
- Choose your packaging style
  - Spring Boot fat/executable JAR (embedded Tomcat/Jetty)
  - Traditional WAR on an external servlet container
  - Plain JAR / non-web application
  - Docker / Kubernetes
- Choose your dependencies
- Choose your storage
- Supply configuration: every mechanism
- Database schema and migrations
- Observability and metrics
- Security
- Verification checklist
- Troubleshooting

## Defaults: everything out of the box

If you add the starter and change nothing, you get:

| Concern | Default |
|---|---|
| Library enabled | `audit.logging.enabled=true` |
| Storage | JPA (`api_audit_log` table) |
| Annotation capture | On (`@AuditLog`) |
| Path-based capture | Off (`policies.path-based.enabled=false`) |
| Sampling | Off (`sampling.enabled=false`, rate 1.0) |
| Async executor | core 5, max 20, queue 1000, policy `CALLER_RUNS` |
| Masking | On, built-in fields (`password`, `token`, `cvv`, `cardNumber`, `secret`, `authorization`) |
| Capture body limit | 1 MiB |
| Internal endpoint | Disabled (fail-secure until `internal.api-key` is set) |
| Flyway migration | Off (`flyway.enabled=false`) |
| Metrics | No-op unless Micrometer is present |

So the minimal "it just works" setup is: **add the starter, provide a database, done.**

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"
    }

with a reachable DataSource. That is enough for JPA-backed annotation auditing.

## Choose your packaging style

### 1. Spring Boot fat/executable JAR (embedded Tomcat/Jetty)

This is the most common deployment for Spring Boot services. The library is bundled inside the JAR
at build time; there is no external folder to place files in.

**Step 1 — add the dependency.**

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"
    }

**Step 2 — build.**

    .\gradlew.bat bootJar

This produces `build/libs/<service>.jar`. The Boot plugin repackages it: your classes plus every
dependency (including the audit JARs) go under `BOOT-INF/lib/`.

**Step 3 — verify the audit JARs are inside.**

    jar tf build/libs/<service>.jar | findstr audit

You should see `BOOT-INF/lib/api-audit-logging-core-2.2.0.jar` and
`BOOT-INF/lib/api-audit-logging-autoconfigure-2.2.0.jar` among others.

**Step 4 — run.**

    java -jar build/libs/<service>.jar

At startup Spring Boot reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
from inside the bundled autoconfigure JAR, activates the audit auto-configuration, and inserts the
audit servlet filter into the embedded server's filter chain.

**What you do NOT do:** do not copy JARs into a `lib/`, `plugins/`, or `webapps/` folder; do not edit
files inside the built JAR; do not expect new JARs dropped next to a running process to be picked up.
Rebuild and restart instead.

### 2. Traditional WAR on an external servlet container

If you deploy a WAR to a standalone Tomcat/Jetty, the library JARs must be on the WAR's classpath at
`WEB-INF/lib/`. The Gradle/Maven build does this automatically; you just change the packaging.

In `build.gradle`, apply the `war` plugin and mark the embedded container as provided:

    plugins {
        id 'war'
    }

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-starter:2.2.0"
        providedRuntime 'org.springframework.boot:spring-boot-starter-tomcat'
    }

Build with `gradlew war` and deploy `build/libs/<service>.war` into the container's `webapps/`.
Because the audit filter is registered through a `FilterRegistrationBean`, the container picks it up
for every request exactly as it does for an embedded server.

If your external container performs classloader isolation per webapp (Tomcat does), the audit JARs
inside `WEB-INF/lib/` are visible to your application only, which is what you want.

### 3. Plain JAR / non-web application

The inbound servlet filter only activates when a servlet web application is present. In a non-web
application (batch job, CLI, message consumer), the library still provides **outbound** client
capture (Feign, RestTemplate, RestClient, WebClient) and manual record publishing, but there are no
HTTP requests to filter.

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-autoconfigure:2.2.0"
        implementation "io.github.puneet-swarup:api-audit-logging-storage-jpa:2.2.0"
        // plus whichever client module you use, e.g. client-resttemplate
    }

The auto-configuration is condition-guarded, so on the classpath without a web application it simply
registers the storage and client integrations and does nothing else.

### 4. Docker / Kubernetes

A container is just packaging around the executable JAR, so the JAR rules still apply. The only
extra concern is configuration and secrets.

**Dockerfile (typical):**

    FROM eclipse-temurin:21-jre
    COPY build/libs/<service>.jar app.jar
    ENTRYPOINT ["java", "-jar", "/app.jar"]

**Supply configuration and secrets via environment variables**, not baked into the image:

    audit:
      logging:
        internal:
          api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}

Then in Kubernetes:

    env:
      - name: AUDIT_LOGGING_INTERNAL_API_KEY
        valueFrom:
          secretKeyRef:
            name: audit-secrets
            key: internal-api-key

Run as a non-root user and keep the audit internal endpoint private (see [Security](#security)).

## Choose your dependencies

The `api-audit-logging-starter` is the convenience aggregator. It brings in:

- `api-audit-logging-core`
- `api-audit-logging-autoconfigure`
- `api-audit-logging-storage-jpa`
- `api-audit-logging-client-feign`
- `api-audit-logging-client-webclient`
- `api-audit-logging-client-resttemplate`

If you do not want all of that (for example you do not use Feign and do not want it pulled in), depend
on the modules you need directly:

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-autoconfigure:2.2.0"
        implementation "io.github.puneet-swarup:api-audit-logging-storage-jpa:2.2.0"
        // add only the clients you use:
        // implementation "io.github.puneet-swarup:api-audit-logging-client-resttemplate:2.2.0"
    }

Module reference:

| Module | Purpose |
|---|---|
| `core` | Annotation, record model, SPIs, masking, filter, listener, internal endpoint |
| `autoconfigure` | Spring Boot auto-configuration (engine, filter, listener) |
| `client-feign` | Feign outbound capture + correlation |
| `client-resttemplate` | RestTemplate, RestClient, HTTP interface capture |
| `client-webclient` | WebClient metadata capture |
| `storage-jpa` | JPA entity, repository, store, Flyway path |
| `storage-jdbc` | JdbcTemplate store (no JPA) |
| `storage-memory` | In-memory store for tests/demos |
| `storage-kafka` | Kafka publishing sink |
| `starter` | Aggregates autoconfigure + JPA + all clients |

Note: when you depend on storage/client modules directly, they are opt-in: nothing activates until you
select it with `audit.logging.storage.type` or the relevant flag.

## Choose your storage

### JPA (default in the starter)

    audit:
      logging:
        storage:
          type: jpa

Best when the application already uses JPA/Spring Data and you want a searchable local store.

### JDBC (no JPA)

    audit:
      logging:
        storage:
          type: jdbc

The same `api_audit_log` table, accessed with `JdbcTemplate`. Use it if you do not want entity
scanning or a persistence provider.

### Memory (tests and demos)

    audit:
      logging:
        storage:
          type: memory

Records live only for the process lifetime. Ideal for integration tests that assert audit behavior
without a database.

### Kafka (streaming sink)

    dependencies {
        implementation "io.github.puneet-swarup:api-audit-logging-autoconfigure:2.2.0"
        implementation "io.github.puneet-swarup:api-audit-logging-storage-kafka:2.2.0"
    }

    audit:
      logging:
        storage:
          type: kafka
        kafka:
          enabled: true
          topic: api-audit-logs

    spring:
      kafka:
        bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

Kafka is a write sink only; it does not power the internal search endpoint. Pair it with a searchable
store if you also need local querying.

## Supply configuration: every mechanism

The library reads standard Spring Boot configuration, so any of these work and they override in the
usual precedence order (later wins):

1. `application.yml` / `application-{profile}.yml` bundled in the JAR
2. An external `application.yml` next to the JAR (`--spring.config.location`)
3. Environment variables
4. Command-line arguments

### Bundled application.yml

    audit:
      logging:
        enabled: true
        policies:
          path-based:
            enabled: true
            rules:
              - pattern: /api/v1/payments/**
                description: Payment APIs

### External file (no rebuild)

Place `application.yml` beside the JAR and run:

    java -jar app.jar --spring.config.location=file:./application.yml

Useful when the same artifact is promoted across environments.

### Environment variables

Spring Boot relaxed binding maps `AUDIT_LOGGING_ENABLED` to `audit.logging.enabled`. Nested list
properties are awkward as env vars, so keep rules in YAML and override scalars via env:

    audit:
      logging:
        internal:
          api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}
        storage:
          type: ${AUDIT_STORAGE_TYPE:jpa}

### Command-line flags

    java -jar app.jar --audit.logging.policies.path-based.enabled=true

### Profiles

Use Spring profiles to switch configuration per environment:

    java -jar app.jar --spring.profiles.active=prod

with `application-prod.yml` carrying the production rules and secrets.

## Database schema and migrations

JPA and JDBC storage both use the `api_audit_log` table. Choose how it is created.

### Option A — let the library run its Flyway migration

    audit:
      logging:
        flyway:
          enabled: true

The library adds its vendor-specific migration path (`classpath:db/audit-migrations/{vendor}`) to your
Flyway run. Vendor is chosen from the JDBC URL (H2, PostgreSQL, MySQL, SQL Server, Oracle).

### Option B — own the schema yourself

Keep `flyway.enabled=false` and copy the library's DDL for your database into your own migration
chain. This is the right choice when a central team owns schema changes.

### Option C — Hibernate ddl-auto (demos only)

    spring:
      jpa:
        hibernate:
          ddl-auto: update

Convenient for local runs, not recommended for production.

## Observability and metrics

If Micrometer is on the classpath, the library registers:

| Meter | Type | Meaning |
|---|---|---|
| `api.audit.records.saved` | counter | Records stored (tagged by `type`) |
| `api.audit.records.failed` | counter | Masking/storage failures |
| `api.audit.records.dropped` | counter | Deliberate drops (`reason` = `QUEUE_FULL` or `SAMPLED`) |
| `api.audit.store.duration` | timer | Store call duration |

To control volume, enable sampling:

    audit:
      logging:
        sampling:
          enabled: true
          sample-rate: 0.1
          always-capture-errors: true

## Security

The internal search endpoint is fail-secure: if no key is configured, it returns 403. Always set the
key from a secret, and prefer network controls on top:

    audit:
      logging:
        internal:
          api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}

    GET /internal/audit-logs
    Header: X-Audit-Api-Key: <key>

Do not commit the key to source control. In production, additionally restrict the endpoint with
Spring Security, a gateway rule, or network policy, and use TLS.

## Verification checklist

After deploying, confirm the library is active:

1. **Startup log** — look for `[AuditLog] Audit Logging Module ready for serviceName=...`.
2. **JAR contents** — `jar tf app.jar | findstr audit` shows the audit JARs under `BOOT-INF/lib/`.
3. **A captured record** — hit an audited endpoint, then query the store:

       SELECT * FROM api_audit_log ORDER BY timestamp DESC

4. **Internal endpoint** (if enabled):

       GET /internal/audit-logs
       Header: X-Audit-Api-Key: <key>

5. **Metrics** (if Micrometer/Actuator present):

       GET /actuator/metrics/api.audit.records.saved

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No `[AuditLog] ... ready` log | Library JAR not on the classpath | Confirm the dependency and that it is inside the fat JAR |
| Log present, nothing captured | Path rule disabled or pattern mismatch | Set `policies.path-based.enabled=true` and check the pattern |
| Annotation endpoints not captured | `policies.annotation.enabled=false` | Enable it or use path rules |
| `api_audit_log` missing | No migration and no ddl-auto | Enable Flyway or create the table |
| 403 on `/internal/audit-logs` | No `internal.api-key` configured (fail-secure) | Set the key |
| Records dropped under load | Executor queue full | Increase `async.queue-capacity`; watch `api.audit.records.dropped` |
| Bodies missing | `capture: METADATA_ONLY` or body too large | Check the rule's capture mode and `capture.max-body-size` |
| Sensitive values present | Field name not covered | Add it to `masking.additional-fields` or use a custom `PayloadMasker` |
| Config change not applied | Config is read at startup | Restart the JVM |

## Deployment matrix (quick reference)

| Deployment | Where audit JARs go | Config source | Schema |
|---|---|---|---|
| Fat JAR (embedded Tomcat) | Inside the JAR (`BOOT-INF/lib/`) | bundled/external YAML, env, flags | Flyway or own |
| WAR (external container) | `WEB-INF/lib/` of the WAR | bundled YAML, container env | Flyway or own |
| Plain JAR (non-web) | Inside the JAR | YAML, env, flags | Flyway or own |
| Docker/K8s | Inside the image/JAR | env vars + secrets | Flyway or own |
| Tests | test classpath | test properties / memory store | in-memory |

## Integration testing with Testcontainers

The storage modules ship integration tests that run the Flyway migrations against real databases and
the Kafka sink against a real broker, using Testcontainers. These tests are **skipped
automatically** when no Docker runtime is available, so they never break a machine without
containers.

To run them, ensure Docker is running and execute:

    .\gradlew.bat test

Covered:

| Module | Test | What it proves |
|---|---|---|
| `storage-jpa` | `AuditLogMigrationPostgresIT` | The PostgreSQL migrations create `api_audit_log` with all columns including `tags` |
| `storage-jpa` | `AuditLogMigrationMysqlIT` | The MySQL migrations create the same table |
| `storage-kafka` | `KafkaAuditLogStoreIT` | A record round-trips through a real broker, keyed by correlation ID |

This is the difference between "the migrations should work" and "the migrations are proven against a
real engine."

## WebClient body capture

By default the WebClient integration captures metadata only. To capture response bodies (which are
buffered and re-published so callers are unaffected):

    audit:
      logging:
        webclient:
          capture-bodies: true

Body size is bounded by `audit.logging.capture.max-body-size`; oversized bodies are stored as a
truncation marker while still being delivered in full to the caller.

## Flyway 10+ database modules

Flyway 10 split database support out of `flyway-core` into per-database modules. If your
application runs migrations with Flyway 10 or newer (Spring Boot 3.5 ships Flyway 11), add the module
for your database:

    dependencies {
        implementation 'org.flywaydb:flyway-core'
        runtimeOnly 'org.flywaydb:flyway-database-postgresql' // or: flyway-mysql
    }

Without the matching module, Flyway fails at startup with an unsupported-database error. The
library's own Testcontainers integration tests add these modules so the vendor migrations are proven
against real engines.
