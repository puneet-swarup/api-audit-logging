package com.api.audit.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Metadata constants for distributed tracing and request correlation.
 *
 * <p>This class serves as the central definition for correlation identifiers used across the
 * library, ensuring consistency between MDC (Mapped Diagnostic Context) keys, HTTP headers, and
 * logging filters.
 *
 * <p>The correlation ID is essential for tracking a single logical request as it traverses multiple
 * microservices or asynchronous thread boundaries.
 *
 * @author Puneet Swarup
 * @see <a href="www.slf4j.org">SLF4J MDC Documentation</a>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CorrelationContext {

  /**
   * The standard HTTP header and MDC key used for propagating the correlation identifier.
   *
   * <p>Value: {@code "X-Correlation-ID"}
   *
   * <p>In the context of the logging library:
   *
   * <ul>
   *   <li><b>Inbound:</b> Extracted from the request header via {@code IncomingLoggingFilter}.
   *   <li><b>Outbound:</b> Injected into Feign requests via {@code correlationInterceptor}.
   *   <li><b>Logging:</b> Included in the application logs to facilitate log aggregation.
   * </ul>
   */
  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
}
