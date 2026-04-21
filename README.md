
![Java Version](https://img.shields.io/badge/Java-21-blue?style=for-the-badge&logo=java)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-brightgreen?style=for-the-badge&logo=springboot)
![License](https://img.shields.io/badge/License-Apache_2.0-orange?style=for-the-badge&logo=apache)
# API Audit & Logging Library


A high-performance, non-invasive auditing library for Spring Boot 3.4+ microservices. This library provides seamless observability for distributed systems by capturing inbound HTTP requests and outbound Feign communication with **zero manual boilerplate**.

---
![Architectural Visual Flow](./assets/architectural-workflow.png)

---
## ⚡ Performance & Resiliency
To ensure zero impact on application latency, the Audit Library utilizes a Fire-and-Forget pattern. All persistence operations are offloaded to a dedicated ThreadPoolTaskExecutor. The behaviour under load is configurable via `audit.logging.async.rejection-policy`.
See the Configuration Reference for all available policies and their trade-offs.
## 🛠️ Installation & Dependency Management

This library is distributed as a JAR. For enterprise environments utilizing a `flatDir` repository structure:

### 1. Configure Repository
In your target project's `build.gradle`, include your local or shared `libs` directory:

```gradle
repositories {
    mavenCentral()
    flatDir {
        dirs "path/to/shared/libs" // Update to your local libs directory path
    }
}
```

### 2. Add Dependency
Add the library to your dependencies block. Note that as a shared library, it requires Java 21 compatibility.
```gradle
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/puneet-swarup/api-audit-logging')
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    implementation 'com.api.audit:api-audit-logging:1.1.0'
    // Spring Web, JPA, and Feign are provided transitively — no need to declare them
}
```

### ⚙️ Configuration Reference

All properties are prefixed with `audit.logging`.

| Property | Default | Description |
|---|---|---|
| `audit.logging.enabled` | `true` | Globally enable/disable the library |
| `audit.logging.async.core-pool-size` | `5` | Core threads in the async log executor |
| `audit.logging.async.max-pool-size` | `20` | Max threads in the async log executor |
| `audit.logging.async.queue-capacity` | `1000` | Queue depth before rejection policy triggers |
| `audit.logging.async.rejection-policy` | `CALLER_RUNS` | `CALLER_RUNS` / `DISCARD_OLDEST` / `DISCARD` / `ABORT` |
| `audit.logging.feign-error.enabled` | `false` | Enable Feign ErrorDecoder for outbound error capture |
| `audit.logging.cleanup.enabled` | `false` | Enable automated daily purge of old logs |
| `audit.logging.cleanup.days` | `30` | Days to retain logs when cleanup is enabled |
| `audit.logging.cleanup.cron` | `0 0 2 * * *` | Cron schedule for cleanup (Spring cron format) |
| `audit.logging.flyway.enabled` | `false` | Let library manage its own Flyway schema migration |
| `audit.logging.internal.api-key` | _(none)_ | API key for `X-Audit-Api-Key` header on `/internal/audit-logs`. If unset, endpoint is blocked (fail-secure). Inject via env var |
| `audit.logging.masking.additional-fields` | `[]` | Extra JSON field names to redact beyond built-in defaults |

> 💡 **IDE support:** Because the library ships with `spring-boot-configuration-processor`,
> all `audit.logging.*` properties have full auto-complete and inline documentation in
> IntelliJ IDEA and VS Code with the Spring Boot extension.
#### Example application.yml
```yaml
audit:
   logging:
      enabled: true
      async:
         core-pool-size: 5
         max-pool-size: 20
         queue-capacity: 1000
         # CALLER_RUNS    → no record lost; caller thread pays latency cost (DEFAULT)
         # DISCARD_OLDEST → oldest queued record dropped; zero latency impact
         # DISCARD        → incoming record dropped; zero latency impact
         # ABORT          → throws RejectedExecutionException
         rejection-policy: CALLER_RUNS
      feign-error:
         enabled: false
      cleanup:
         enabled: false
         days: 15
         cron: "0 0 2 * * *"
```

### 🔒 Securing the Internal Endpoint

The `/internal/audit-logs` endpoint is protected by an API key filter.
Configure a secret key and provide it in the `X-Audit-Api-Key` header:

```yaml
audit:
  logging:
    internal:
      api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}
```

**Calling the endpoint:**

### 📖 Usage Patterns
1. Declarative Auditing
   Apply the '@AuditLog' annotation to Controllers or Feign Clients. The library uses an interceptor to detect this annotation at the class or method level.
```java
@RestController
@AuditLog("User Management")
public class UserController {

    @PostMapping("/sync")
    @AuditLog("Sync my Data")
    public ResponseEntity<Void> sync() { ... }
}
```
2. Safe Feign Chaining
   When _audit.logging.feign-error.enabled_ is true, the library uses a Decorator Pattern to wrap your existing 
   ErrorDecoder. This ensures your custom business exceptions are still thrown after the audit data is captured.

### 🛡️ Safety & Architecture
- Java 21 Optimized: Leverages modern JVM capabilities for high-throughput logging.
- Application Resiliency: Logging persistence is asynchronous. It uses a dedicated `logExecutor` whose saturation behaviour is configurable
  via `audit.logging.async.rejection-policy`. The default (`CALLER_RUNS`) ensures
  no audit record is ever lost. See Configuration Reference for all options.
- Automated Redaction: An internal JsonMasker automatically redacts sensitive fields (e.g., password, token, secret, authorization) from all JSON payloads before persistence.
- Stable Lifecycle Management: Utilizes static BeanPostProcessors for JPA configuration, preventing premature bean instantiation and ensuring Spring container stability.
### 🔍 Observability Support
The library exposes an internal management endpoint for log retrieval, designed for integration with support dashboards:
```
GET /internal/audit-logs?correlationId=xyz&type=INCOMING
```

**Available Filters**: correlationId, type (INCOMING/OUTGOING), serviceName, and timestamp ranges.

### 🧪 Testing Integration
To disable auditing during integration tests and prevent unnecessary database interactions:
````java
@SpringBootTest
@TestPropertySource(properties = {
    "audit.logging.enabled=false",
    "audit.logging.feign-error.enabled=false"
})
class ApplicationIntegrationTest { ... }
````