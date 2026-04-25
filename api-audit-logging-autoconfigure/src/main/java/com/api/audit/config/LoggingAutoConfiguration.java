package com.api.audit.config;

import com.api.audit.context.CorrelationContext;
import com.api.audit.feign.CustomFeignErrorDecoder;
import com.api.audit.feign.CustomFeignLogger;
import com.api.audit.filter.AuditLogSecurityFilter;
import com.api.audit.filter.IncomingLoggingFilter;
import com.api.audit.interceptor.AuditLogInterceptor;
import com.api.audit.util.JsonMasker;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * Main auto-configuration class for the API Audit and Logging Library.
 *
 * <p>This configuration activates a comprehensive auditing suite, including:
 *
 * <ul>
 *   <li>Inbound HTTP request/response logging via {@link IncomingLoggingFilter}.
 *   <li>Outbound Feign client logging via {@link CustomFeignLogger}.
 *   <li>Persistence-layer auditing via {@link AuditLogInterceptor}.
 *   <li>Asynchronous execution for logging tasks to prevent latency in the primary request path.
 *   <li>Correlation ID propagation across distributed boundaries.
 * </ul>
 *
 * <p>Activation is controlled by the property {@code audit.logging.enabled}, which defaults to
 * {@code true}.
 *
 * @author Puneet Swarup
 */
@Configuration
@Import(LoggingJpaConfig.class)
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = "com.api.audit")
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnProperty(prefix = "audit.logging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LoggingAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  private final AuditLoggingProperties properties;

  /** Prints the library banner on startup. */
  @PostConstruct
  public void printAuditBanner() {
    System.out.println(
        "\n\n  ,---.  ,------. ,--.   ,---.             ,--.,--.  ,--.   ,--.                      ,--.      "
            + "          ");
    System.out.println(
        " /  O  \\ |  .--. '|  |  /  O  \\ ,--.,--. ,-|  |`--',-'  '-. |  |    ,---. ,---. ,---. "
            + "`--',--,--,  ,---.  ");
    System.out.println(
        "|  .-.  ||  '--' ||  | |  .-.  ||  ||  |' .-. |,--.'-.  .-' |  |   | .-. | .-. | .-. |,--"
            + ".|      \\| .-. | ");
    System.out.println(
        "|  | |  ||  | --' |  | |  | |  |'  ''  '\\ `-' ||  |  |  |   |  '--.' '-' ' '-' ' '-' '| "
            + " ||  ||  |' '-' ' ");
    System.out.println(
        "`--' `--'`--'     `--' `--' `--' `----'  `---' `--'  `--'   `-----' `---'.`-  /.`-  / `--'`--''--'.`-  /  ");
    System.out.println(
        "                                                                         `---' `---'              `---'   ");
    System.out.println("\nAudit Logging Module - Service Name: " + appName + "\n");
    if ("unknown-service".equals(appName)) {
      System.out.println("⚠️ WARN: spring.application.name is not set in the host service.\n");
    }
  }

  /** Registers the {@link AuditLogInterceptor} for all incoming web requests. */
  @Bean
  public MappedInterceptor auditInterceptors(AuditLogInterceptor auditLogInterceptor) {
    return new MappedInterceptor(new String[] {"/**"}, auditLogInterceptor);
  }

  /**
   * Defines the thread pool dedicated to asynchronous logging operations.
   *
   * <p>The rejection policy when the queue is full is configurable via {@code
   * audit.logging.async.rejection-policy}:
   *
   * <ul>
   *   <li>{@code CALLER_RUNS} — no record lost; caller thread pays latency cost (default)
   *   <li>{@code DISCARD_OLDEST} — oldest queued record dropped; zero caller latency
   *   <li>{@code DISCARD} — incoming record dropped; zero caller latency
   *   <li>{@code ABORT} — throws RejectedExecutionException
   * </ul>
   *
   * <p>All policies emit a WARN log on saturation to aid capacity tuning.
   */
  @Bean(name = "logExecutor")
  public Executor logExecutor() {
    AuditLoggingProperties.Async asyncProps = properties.getAsync();

    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(asyncProps.getCorePoolSize());
    executor.setMaxPoolSize(asyncProps.getMaxPoolSize());
    executor.setQueueCapacity(asyncProps.getQueueCapacity());
    executor.setThreadNamePrefix("AuditLog-");
    executor.setRejectedExecutionHandler(buildRejectionHandler(asyncProps.getRejectionPolicy()));
    executor.initialize();

    log.info(
        "[AuditLog] Executor ready — corePool={}, maxPool={}, queue={}, policy={}",
        asyncProps.getCorePoolSize(),
        asyncProps.getMaxPoolSize(),
        asyncProps.getQueueCapacity(),
        asyncProps.getRejectionPolicy());

    return executor;
  }

  /**
   * Builds the {@link RejectedExecutionHandler} for the configured policy.
   *
   * <p>Every policy variant emits a WARN log when triggered so saturation is always observable
   * regardless of which policy the developer chose.
   */
  private RejectedExecutionHandler buildRejectionHandler(AuditRejectionPolicy policy) {
    return switch (policy) {
      case CALLER_RUNS ->
          new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
              log.warn(
                  "[AuditLog] Queue full (CALLER_RUNS) — caller thread will persist this record."
                      + " Consider increasing audit.logging.async.queue-capacity.");
              super.rejectedExecution(r, e);
            }
          };

      case DISCARD_OLDEST ->
          new ThreadPoolExecutor.DiscardOldestPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
              log.warn(
                  "[AuditLog] Queue full (DISCARD_OLDEST) — oldest pending audit record dropped."
                      + " Consider increasing audit.logging.async.queue-capacity.");
              super.rejectedExecution(r, e);
            }
          };

      case DISCARD ->
          new ThreadPoolExecutor.DiscardPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
              log.warn(
                  "[AuditLog] Queue full (DISCARD) — incoming audit record dropped."
                      + " Consider increasing audit.logging.async.queue-capacity.");
              super.rejectedExecution(r, e);
            }
          };

      case ABORT ->
          new ThreadPoolExecutor.AbortPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
              log.warn("[AuditLog] Queue full (ABORT) — throwing RejectedExecutionException.");
              super.rejectedExecution(r, e);
            }
          };
    };
  }

  /** Configures the filter responsible for intercepting and logging raw inbound HTTP traffic. */
  @Bean
  public IncomingLoggingFilter incomingLoggingFilter(ApplicationEventPublisher publisher) {
    return new IncomingLoggingFilter(publisher, appName);
  }

  /** Registers the {@link IncomingLoggingFilter} with highest precedence in the filter chain. */
  @Bean
  public FilterRegistrationBean<IncomingLoggingFilter> loggingFilterRegistration(
      IncomingLoggingFilter filter) {
    FilterRegistrationBean<IncomingLoggingFilter> bean = new FilterRegistrationBean<>(filter);
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  /** Provides a custom Feign logger to capture outbound communication. */
  @Bean
  public feign.Logger feignLogger(ApplicationEventPublisher p) {
    return new CustomFeignLogger(p, appName);
  }

  /** Sets the Feign logging level to FULL. */
  @Bean
  public feign.Logger.Level feignLevel() {
    return feign.Logger.Level.FULL;
  }

  /** Feign RequestInterceptor that propagates the correlation ID to outbound requests. */
  @Bean
  public RequestInterceptor correlationInterceptor() {
    return template -> {
      String id = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
      if (id != null) template.header(CorrelationContext.CORRELATION_ID_HEADER, id);
    };
  }

  /**
   * Configures the Feign ErrorDecoder with auditing capability via the Decorator Pattern.
   *
   * <p>Activation: set {@code audit.logging.feign-error.enabled=true}.
   */
  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "audit.logging.feign-error",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public ErrorDecoder errorDecoder(
      ApplicationEventPublisher p,
      java.util.List<ErrorDecoder> existingDecoders,
      JsonMasker jsonMasker) {

    ErrorDecoder serviceDecoder =
        existingDecoders.stream()
            .filter(d -> !(d instanceof CustomFeignErrorDecoder))
            .findFirst()
            .orElse(new ErrorDecoder.Default());

    return new CustomFeignErrorDecoder(p, appName, serviceDecoder, jsonMasker);
  }

  /**
   * Registers the {@link AuditLogSecurityFilter} to protect the internal audit endpoint.
   *
   * <p>This filter runs at a higher precedence than the main logging filter to ensure
   * unauthenticated requests are rejected before any request processing occurs.
   */
  @Bean
  public FilterRegistrationBean<AuditLogSecurityFilter> auditSecurityFilterRegistration() {
    AuditLogSecurityFilter filter = new AuditLogSecurityFilter(properties);
    FilterRegistrationBean<AuditLogSecurityFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.addUrlPatterns("/internal/audit-logs*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
