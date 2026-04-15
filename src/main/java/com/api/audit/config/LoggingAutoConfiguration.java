package com.api.audit.config;

import com.api.audit.context.CorrelationContext;
import com.api.audit.feign.CustomFeignErrorDecoder;
import com.api.audit.feign.CustomFeignLogger;
import com.api.audit.filter.IncomingLoggingFilter;
import com.api.audit.interceptor.AuditLogInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.handler.MappedInterceptor;

/**
 * Main auto-configuration class for the Chola Audit and Logging Library.
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
 * @see <a href="docs.spring.io">Spring Auto-configuration</a>
 */
@Configuration
@Import(LoggingJpaConfig.class)
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = "com.api.audit")
@ConditionalOnProperty(prefix = "audit.logging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LoggingAutoConfiguration {

  @Value("${spring.application.name:unknown-service}")
  private String appName;

  /**
   * Diagnostic method that executes immediately after the bean initialization.
   *
   * <p>This method prints a visual ASCII banner and a status message to the logs to confirm that
   * the Audit Logging module has been successfully loaded.
   *
   * <p>Note: This will only execute if the {@code audit.logging.enabled} property is set to {@code
   * true}, as governed by the class-level {@link
   * org.springframework.boot.autoconfigure.condition.ConditionalOnProperty} annotation.
   */
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

  /**
   * Registers the {@link AuditLogInterceptor} for all incoming web requests.
   *
   * @param auditLogInterceptor the interceptor responsible for capturing audit events
   * @return a {@link MappedInterceptor} applied to the "/**" path pattern
   */
  @Bean
  public MappedInterceptor auditInterceptors(AuditLogInterceptor auditLogInterceptor) {
    return new MappedInterceptor(new String[] {"/**"}, auditLogInterceptor);
  }

  /**
   * Defines the thread pool dedicated to asynchronous logging operations.
   *
   * <p>The executor uses a {@link ThreadPoolExecutor.DiscardOldestPolicy} to ensure that in the
   * event of a system saturation, the main application performance is prioritized over log
   * retention.
   *
   * @return an {@link Executor} configured with a core size of 5 and max size of 20
   */
  @Bean(name = "logExecutor")
  public Executor logExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("AuditLog-");
    // CRITICAL: If queue is full, the log is simply dropped to save the main audit
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
    executor.initialize();
    return executor;
  }

  /**
   * Configures the filter responsible for intercepting and logging raw inbound HTTP traffic.
   *
   * @param publisher the event publisher used to broadcast logging events
   * @return an initialized {@link IncomingLoggingFilter}
   */
  @Bean
  public IncomingLoggingFilter incomingLoggingFilter(ApplicationEventPublisher publisher) {
    return new IncomingLoggingFilter(publisher, appName);
  }

  /**
   * Registers the {@link IncomingLoggingFilter} with the highest precedence in the filter chain.
   * This ensures correlation IDs and request logs are captured before any business logic or
   * security constraints execute.
   *
   * @param filter the logging filter
   * @return the registration bean with {@link Ordered#HIGHEST_PRECEDENCE}
   */
  @Bean
  public FilterRegistrationBean<IncomingLoggingFilter> loggingFilterRegistration(
      IncomingLoggingFilter filter) {
    FilterRegistrationBean<IncomingLoggingFilter> bean = new FilterRegistrationBean<>(filter);
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  /**
   * Provides a custom Feign logger to capture outbound communication.
   *
   * @param p the event publisher
   * @return a {@link CustomFeignLogger} instance
   */
  @Bean
  public feign.Logger feignLogger(ApplicationEventPublisher p) {
    return new CustomFeignLogger(p, appName);
  }

  /**
   * Sets the Feign logging level to {@code FULL}.
   *
   * <p>Note: This is necessary to ensure the {@code feignLogger} bean receives all request and
   * response metadata.
   *
   * @return the {@link feign.Logger.Level#FULL} level
   */
  @Bean
  public feign.Logger.Level feignLevel() {
    return feign.Logger.Level.FULL; // Required to trigger the logger
  }

  /**
   * A Feign {@link RequestInterceptor} that extracts the correlation ID from the Slf4j MDC context
   * and injects it into outbound HTTP headers.
   *
   * @return a request interceptor for distributed tracing
   */
  @Bean
  public RequestInterceptor correlationInterceptor() {
    return template -> {
      String id = MDC.get(CorrelationContext.CORRELATION_ID_HEADER);
      if (id != null) template.header(CorrelationContext.CORRELATION_ID_HEADER, id);
    };
  }

  /**
   * Configures the Feign ErrorDecoder with a delegation chain.
   *
   * <p>This bean is marked as {@link org.springframework.context.annotation.Primary} to ensure that
   * Feign selects this auditing wrapper as the entry point for error decoding. It automatically
   * detects if the host service has defined its own {@link feign.codec.ErrorDecoder} and wraps it;
   * otherwise, it wraps the Feign {@link ErrorDecoder.Default}.
   *
   * <p><b>Activation:</b> This bean is DISABLED by default. To enable it, set {@code
   * audit.logging.feign-error.enabled=true} in your application properties.
   *
   * <p><b>Safe Chaining:</b> When enabled, it automatically detects any existing service-level
   * {@link ErrorDecoder} and wraps it to ensure business exceptions are still propagated after
   * auditing.
   *
   * @param p the event publisher
   * @param existingDecoders a list of all ErrorDecoder beans currently in the context
   * @return a primary ErrorDecoder that performs auditing before delegating
   */
  @Bean
  @Primary
  @ConditionalOnProperty(
      prefix = "audit.logging.feign-error",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public ErrorDecoder errorDecoder(
      ApplicationEventPublisher p, java.util.List<ErrorDecoder> existingDecoders) {

    // Filter out this library's own decoder to prevent infinite recursion
    ErrorDecoder serviceDecoder =
        existingDecoders.stream()
            .filter(d -> !(d instanceof CustomFeignErrorDecoder))
            .findFirst()
            .orElse(new ErrorDecoder.Default());

    return new CustomFeignErrorDecoder(p, appName, serviceDecoder);
  }
}
