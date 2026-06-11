package com.api.audit.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Centralised configuration properties for the API Audit Logging library.
 *
 * <p>All properties are prefixed with {@code audit.logging}. Example {@code application.yml}:
 *
 * <pre>{@code
 * audit:
 *   logging:
 *     enabled: true
 *     async:
 *       rejection-policy: CALLER_RUNS
 *     cleanup:
 *       enabled: true
 *       days: 30
 *     storage:
 *       type: jpa
 *     feign-error:
 *       enabled: false
 * }</pre>
 *
 * @author Puneet Swarup
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "audit.logging")
public class AuditLoggingProperties {

  /**
   * Globally enable or disable the audit logging library.
   *
   * <p>When {@code false}, no beans are registered and no HTTP traffic is intercepted. Default:
   * {@code true}.
   */
  private boolean enabled = true;

  /**
   * The name of the service that will appear in every audit log record. Defaults to the value of
   * {@code spring.application.name}.
   */
  // Note: spring.application.name is bound separately in LoggingAutoConfiguration
  // via constructor injection since it lives under a different prefix.
  // This field holds the audit-specific service name override if needed.

  @NestedConfigurationProperty private Async async = new Async();

  @NestedConfigurationProperty private Cleanup cleanup = new Cleanup();

  @NestedConfigurationProperty private Capture capture = new Capture();

  @NestedConfigurationProperty private FeignError feignError = new FeignError();

  @NestedConfigurationProperty private Flyway flyway = new Flyway();

  @NestedConfigurationProperty private Kafka kafka = new Kafka();

  @NestedConfigurationProperty private Storage storage = new Storage();

  @NestedConfigurationProperty private Internal internal = new Internal();

  @NestedConfigurationProperty private Masking masking = new Masking();

  /** Configuration for the asynchronous {@code logExecutor} thread pool. */
  @Getter
  @Setter
  public static class Async {

    /** Core number of threads kept alive in the log executor pool. Default: {@code 5}. */
    private int corePoolSize = 5;

    /** Maximum number of threads allowed in the log executor pool. Default: {@code 20}. */
    private int maxPoolSize = 20;

    /**
     * Capacity of the bounded task queue.
     *
     * <p>Tune this to: {@code peak_requests_per_second * avg_db_write_ms / 1000}. Default: {@code
     * 1000}.
     */
    private int queueCapacity = 1000;

    /**
     * Policy applied when the executor queue is full.
     *
     * <ul>
     *   <li>{@code CALLER_RUNS} - no record lost; caller thread pays latency (default)
     *   <li>{@code DISCARD_OLDEST} - oldest queued record dropped; zero caller latency
     *   <li>{@code DISCARD} - incoming record dropped; zero caller latency
     *   <li>{@code ABORT} - throws RejectedExecutionException
     * </ul>
     *
     * Default: {@code CALLER_RUNS}.
     */
    private AuditRejectionPolicy rejectionPolicy = AuditRejectionPolicy.CALLER_RUNS;
  }

  /** Configuration that bounds how much request/response data is copied into audit records. */
  @Getter
  @Setter
  public static class Capture {

    /**
     * Maximum payload body bytes captured for request or response bodies.
     *
     * <p>When a body is larger than this limit, the audit record stores a clear truncation marker
     * instead of copying the whole payload. Default: {@code 1048576} (1 MiB).
     */
    private int maxBodySize = 1024 * 1024;

    /**
     * Maximum serialized header bytes captured for request or response headers.
     *
     * <p>Headers are redacted before this limit is applied. Default: {@code 20000}.
     */
    private int maxHeaderSize = 20_000;

    /**
     * Ant-style request path patterns allowed for inbound capture.
     *
     * <p>The default {@code /**} keeps existing behavior. Use this when a service wants auditing
     * only for a known API surface, for example {@code /api/**}.
     */
    private List<String> includedPaths = new ArrayList<>(List.of("/**"));

    /**
     * Ant-style request path patterns excluded from inbound capture.
     *
     * <p>Exclusions win over inclusions. This is useful for health checks, static assets, or
     * endpoints whose payloads should never be copied into an audit record.
     */
    private List<String> excludedPaths = new ArrayList<>();
  }

  /** Configuration for automated audit log data retention and cleanup. */
  @Getter
  @Setter
  public static class Cleanup {

    /** Enable the scheduled daily purge of old audit records. Default: {@code false}. */
    private boolean enabled = false;

    /**
     * Number of days to retain audit records before they are eligible for deletion. Only used when
     * {@code audit.logging.cleanup.enabled=true}. Default: {@code 30}.
     */
    private int days = 30;

    /**
     * Cron expression controlling the cleanup schedule (Spring cron format).
     *
     * <p>Format: {@code second minute hour day-of-month month day-of-week} Default: {@code "0 0 2 *
     * * *"} (daily at 2:00 AM).
     *
     * <p>Example overrides:
     *
     * <ul>
     *   <li>{@code "0 30 1 * * *"} — daily at 1:30 AM
     *   <li>{@code "0 0 3 * * SUN"} — every Sunday at 3:00 AM
     * </ul>
     */
    private String cron = "0 0 2 * * *";
  }

  /** Configuration for Feign outbound error capture. */
  @Getter
  @Setter
  public static class FeignError {

    /**
     * Enable the {@code CustomFeignErrorDecoder} to capture outbound error response bodies.
     *
     * <p>When enabled, uses a Decorator Pattern to wrap any existing {@code ErrorDecoder} in the
     * host application, ensuring custom business exceptions are still propagated. Default: {@code
     * false}.
     */
    private boolean enabled = false;
  }

  /** Configuration for the library's Flyway schema migration. */
  @Getter
  @Setter
  public static class Flyway {

    /**
     * Whether the audit library should manage its own schema via Flyway.
     *
     * <p>When {@code false} (default), the library's Flyway customizer is disabled. Host is
     * responsible for running the library's migration script manually. Vendor-specific scripts are
     * available under {@code classpath:db/audit-migrations/{database}} inside the storage module
     * JAR.
     *
     * <p>When {@code true}, the library registers a {@link
     * org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer} that adds {@code
     * classpath:db/audit-migrations} to host's Flyway migration locations.
     *
     * <p>Default: {@code false}.
     */
    private boolean enabled = false;
  }

  /** Configuration for the optional Kafka audit sink. */
  @Getter
  @Setter
  public static class Kafka {

    /**
     * Enable Kafka as the active audit sink when the Kafka storage module is present.
     *
     * <p>This is opt-in because publishing audit records to a stream usually needs an explicit
     * operational decision: topic naming, retention, ACLs, and downstream consumers should be known
     * before traffic starts flowing.
     */
    private boolean enabled = false;

    /** Kafka topic used by the built-in Kafka sink. */
    private String topic = "api-audit-logs";
  }

  /** Configuration for selecting one of the built-in searchable storage implementations. */
  @Getter
  @Setter
  public static class Storage {

    /**
     * Built-in storage implementation to prefer when more than one storage module is present.
     *
     * <p>Supported values: {@code jpa}, {@code jdbc}, {@code memory}. When omitted, the starter
     * keeps its original JPA-first behavior. Kafka remains controlled by {@code
     * audit.logging.kafka.enabled} because it is a streaming sink rather than a searchable local
     * store.
     */
    private String type;
  }

  /** Security configuration for the internal audit log management endpoint. */
  @Getter
  @Setter
  public static class Internal {

    /**
     * API key required in the {@code X-Audit-Api-Key} request header to access the {@code
     * /internal/audit-logs} endpoint.
     *
     * <p>If this value is blank or not set, the endpoint is blocked entirely (fail-secure). No
     * audit data can be retrieved without a configured key.
     *
     * <p><b>Security guidance:</b> Never set this value directly in {@code application.yml} checked
     * into source control. Instead, inject it via an environment variable:
     *
     * <pre>{@code
     * audit:
     *   logging:
     *     internal:
     *       api-key: ${AUDIT_LOGGING_INTERNAL_API_KEY:}
     * }</pre>
     */
    private String apiKey;
  }

  /** Configuration for sensitive field masking in JSON payloads. */
  @Getter
  @Setter
  public static class Masking {

    /**
     * Additional JSON field names to redact beyond the built-in defaults.
     *
     * <p>Matching is case-insensitive. The field name only needs to appear anywhere inside the JSON
     * key (contains-check), so {@code "card"} would match both {@code "cardNumber"} and {@code
     * "debitCard"}.
     *
     * <p>Built-in defaults (always masked, not overridable): {@code password, token, cvv,
     * cardNumber, secret, authorization}.
     *
     * <p>Example:
     *
     * <pre>{@code
     * audit:
     *   logging:
     *     masking:
     *       additional-fields:
     *         - otp
     *         - nationalId
     *         - pin
     * }</pre>
     */
    private List<String> additionalFields = new ArrayList<>();
  }
}
