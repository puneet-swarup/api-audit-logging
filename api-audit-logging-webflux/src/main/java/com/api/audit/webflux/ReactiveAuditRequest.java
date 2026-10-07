package com.api.audit.webflux;

import com.api.audit.spi.AuditRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.server.ServerWebExchange;

/**
 * {@link AuditRequest} adapter over a reactive {@link ServerWebExchange}.
 *
 * <p>Reactive requests have no mutable attribute map, so this adapter keeps its own attribute map
 * for the lifetime of the exchange. That is sufficient for the annotation interceptor to record
 * that a handler was annotated before the capture filter reads the decision.
 *
 * @author Puneet Swarup
 */
public class ReactiveAuditRequest implements AuditRequest {

  private final ServerWebExchange exchange;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();

  /**
   * Creates the adapter.
   *
   * @param exchange the current exchange; never {@code null}
   */
  public ReactiveAuditRequest(ServerWebExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public String getMethod() {
    return exchange.getRequest().getMethod().name();
  }

  @Override
  public String getPath() {
    return exchange.getRequest().getURI().getRawPath();
  }

  @Override
  public String getHeader(String name) {
    return exchange.getRequest().getHeaders().getFirst(name);
  }

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    attributes.put(name, value);
  }
}
