package com.api.audit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

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
@Validated
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

  @NestedConfigurationProperty private WebClient webclient = new WebClient();

  @NestedConfigurationProperty private FileSink file = new FileSink();

  @NestedConfigurationProperty private FeignError feignError = new FeignError();

  @NestedConfigurationProperty private Flyway flyway = new Flyway();

  @NestedConfigurationProperty private Kafka kafka = new Kafka();

  @NestedConfigurationProperty private Storage storage = new Storage();

  @NestedConfigurationProperty private Internal internal = new Internal();

  @NestedConfigurationProperty private Masking masking = new Masking();

  @Valid @NestedConfigurationProperty private Sampling sampling = new Sampling();

  @Valid @NestedConfigurationProperty private Policies policies = new Policies();

  /** Configuration for the asynchronous {@code logExecutor} thread pool. */
  @Getter
  @Setter
  public static class Async {

    /** Core number of threads kept alive in the log executor pool. Default: {@code 5}. */
    @Positive private int corePoolSize = 5;

    /** Maximum number of threads allowed in the log executor pool. Default: {@code 20}. */
    @Positive private int maxPoolSize = 20;

    /**
     * Capacity of the bounded task queue.
     *
     * <p>Tune this to: {@code peak_requests_per_second * avg_db_write_ms / 1000}. Default: {@code
     * 1000}.
     */
    @Positive private int queueCapacity = 1000;

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
    @Positive private int maxBodySize = 1024 * 1024;

    /**
     * Maximum serialized header bytes captured for request or response headers.
     *
     * <p>Headers are redacted before this limit is applied. Default: {@code 20000}.
     */
    @Positive private int maxHeaderSize = 20_000;

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
    @Min(1)
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
    @NotBlank private String topic = "api-audit-logs";
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
    @Pattern(
        regexp = "jpa|jdbc|memory|kafka|file|stdout",
        message =
            "audit.logging.storage.type must be one of: jpa, jdbc, memory, kafka, file, stdout")
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

  /**
   * Configuration for WebClient outbound capture.
   *
   * <p>By default the WebClient integration captures metadata only (method, URL, status, timing,
   * headers, correlation ID) because consuming reactive bodies requires re-publishing them. When
   * {@link #captureBodies} is enabled the integration additionally buffers request and response
   * bodies and re-publishes them downstream, so normal application processing is unaffected.
   */
  @Getter
  @Setter
  public static class WebClient {

    /**
     * Whether to capture WebClient request and response bodies. Default: {@code false}.
     *
     * <p>When enabled, bodies are buffered in memory and re-published for downstream subscribers.
     * Each body is bounded by {@code audit.logging.capture.max-body-size}. Enabling this has a
     * small memory cost per in-flight exchange; leave it off for high-throughput streaming
     * endpoints.
     */
    private boolean captureBodies = false;
  }

  /**
   * Configuration for the JSON-lines file sink.
   *
   * <p>Used when {@code audit.logging.storage.type=file}. Records are written as one JSON object
   * per line to the configured path.
   */
  @Getter
  @Setter
  public static class FileSink {

    /**
     * Destination file for the JSON-lines sink. Parent directories are created if missing. Default:
     * {@code audit.log}. Ignored when {@code storage.type=stdout}.
     */
    private String path = "audit.log";
  }

  /**
   * Configuration for audit record sampling.
   *
   * <p>Sampling reduces audit volume by storing only a fraction of successful records while always
   * storing errors. It is disabled by default so behavior is unchanged unless configured.
   */
  @Getter
  @Setter
  public static class Sampling {

    /**
     * Whether sampling is active. Default: {@code false}.
     *
     * <p>When {@code false}, every captured record is stored.
     */
    private boolean enabled = false;

    /**
     * The fraction of non-error records to store, in {@code [0.0, 1.0]}. Default: {@code 1.0}.
     *
     * <p>For example {@code 0.1} stores roughly one in ten successful records. Values below {@code
     * 1.0} only take effect when {@link #enabled} is {@code true}.
     */
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double sampleRate = 1.0;

    /**
     * Whether error records bypass sampling and are always stored. Default: {@code true}.
     *
     * <p>Keeping this enabled ensures failures are never lost to sampling, which is usually the
     * reason an operator turns sampling on in the first place.
     */
    private boolean alwaysCaptureErrors = true;
  }

  /**
   * Configuration for the audit policy engine.
   *
   * <p>The engine combines the annotation policy and the path-based policy into a single decision
   * per request. Custom policies supplied as beans are also evaluated.
   *
   * <p>This block is the entry point for the "add the library and configure paths, without changing
   * application code" mode. See {@code policies.path-based}.
   */
  @Getter
  @Setter
  public static class Policies {

    @NestedConfigurationProperty private AnnotationPolicy annotation = new AnnotationPolicy();

    @Valid @NestedConfigurationProperty private PathBasedPolicy pathBased = new PathBasedPolicy();
  }

  /**
   * Configuration for the annotation-based policy.
   *
   * <p>This policy opts a request into auditing when the controller method or class carries {@link
   * com.api.audit.annotation.AuditLog}. It is enabled by default to preserve backward compatibility
   * with applications that already annotate their controllers.
   */
  @Getter
  @Setter
  public static class AnnotationPolicy {

    /**
     * Whether the annotation policy is evaluated. Default: {@code true}.
     *
     * <p>Set this to {@code false} to run the library entirely in path-based (config-only) mode.
     */
    private boolean enabled = true;
  }

  /**
   * Configuration for the path-based policy.
   *
   * <p>When enabled, requests whose path matches a rule are audited even when no {@code @AuditLog}
   * annotation is present. This is the zero-code-change mode: add the library dependency, declare
   * the rules in configuration, and restart the JVM.
   *
   * <p>Rules are evaluated from the most specific to the least specific. A rule with {@code audit:
   * false} is an explicit skip and always wins.
   */
  @Getter
  @Setter
  public static class PathBasedPolicy {

    /**
     * Whether path-based auditing is active. Default: {@code false}.
     *
     * <p>Path-based auditing is opt-in so that merely adding the library does not start capturing
     * traffic. Enable it explicitly and declare at least one rule under {@code rules}.
     */
    private boolean enabled = false;

    /**
     * Ordered list of path rules. An empty list means the policy never matches.
     *
     * <p>Specificity is computed by the policy so that more specific rules (longer patterns, fewer
     * wildcards, and an explicit HTTP method) take precedence over broader ones, regardless of the
     * order they appear in configuration.
     */
    private List<PathRule> rules = new ArrayList<>();
  }

  /**
   * A single path-based audit rule.
   *
   * <p>Example:
   *
   * <pre>{@code
   * audit:
   *   logging:
   *     policies:
   *       path-based:
   *         enabled: true
   *         rules:
   *           - pattern: /api/v1/payments/**
   *             description: Payment APIs
   *             methods: [POST, PUT]
   *             capture: FULL
   *             tags:
   *               module: payments
   *               tier: critical
   *           - pattern: /internal/**
   *             audit: false
   * }</pre>
   */
  @Getter
  @Setter
  public static class PathRule {

    /**
     * The path pattern to match. Interpretation depends on {@link #matcher}. Default matcher is
     * {@link com.api.audit.policy.PathMatcherType#ANT}.
     */
    @NotBlank private String pattern;

    /**
     * The matching strategy for {@link #pattern}. Default: {@link
     * com.api.audit.policy.PathMatcherType#ANT}.
     */
    private com.api.audit.policy.PathMatcherType matcher = com.api.audit.policy.PathMatcherType.ANT;

    /**
     * Whether matching requests are audited. Default: {@code true}.
     *
     * <p>Set to {@code false} to create an explicit skip rule. Skip rules always win over positive
     * rules and over the annotation policy.
     */
    private boolean audit = true;

    /**
     * HTTP methods this rule applies to. When empty, the rule applies to every method. Matching is
     * case-insensitive.
     */
    private List<String> methods = new ArrayList<>();

    /**
     * Human-readable description copied into the audit record when this rule matches. When blank,
     * the policy falls back to {@code "<METHOD> <path>"}.
     */
    private String description;

    /**
     * How much of the payload to capture when this rule matches. Default: {@link
     * com.api.audit.policy.CaptureMode#FULL}.
     */
    private com.api.audit.policy.CaptureMode capture = com.api.audit.policy.CaptureMode.FULL;

    /** Custom key/value dimensions attached to matching records. */
    private Map<String, String> tags = new LinkedHashMap<>();
  }
}
