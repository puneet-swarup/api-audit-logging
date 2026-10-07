package com.api.audit.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.event.ApiLogEvent;
import com.api.audit.model.AuditLogRecord;
import com.api.audit.policy.AnnotationAuditPolicy;
import com.api.audit.policy.AuditDecisionEngine;
import com.api.audit.policy.PathAuditPolicy;
import com.api.audit.policy.SamplingStrategy;
import com.api.audit.spi.NoOpAuditMetrics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link ReactiveIncomingAuditFilter}.
 *
 * <p>Proves that path-based rules capture reactive inbound requests and that non-matching paths
 * pass through untouched.
 *
 * @author Puneet Swarup
 */
class ReactiveIncomingAuditFilterTest {

  private final List<ApiLogEvent> events = new ArrayList<>();
  private final ApplicationEventPublisher publisher = e -> events.add((ApiLogEvent) e);
  private ReactiveIncomingAuditFilter filter;

  private AuditLoggingProperties propertiesFor(String pattern) {
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getPolicies().getPathBased().setEnabled(true);
    AuditLoggingProperties.PathRule rule = new AuditLoggingProperties.PathRule();
    rule.setPattern(pattern);
    rule.setDescription("Reactive path rule");
    props.getPolicies().getPathBased().setRules(List.of(rule));
    return props;
  }

  private void buildFilter(AuditLoggingProperties props) {
    AuditDecisionEngine engine =
        new AuditDecisionEngine(
            List.of(new AnnotationAuditPolicy(true), new PathAuditPolicy(props)));
    SamplingStrategy sampling = new SamplingStrategy(false, 1.0, true);
    filter =
        new ReactiveIncomingAuditFilter(
            publisher, "reactive-svc", props, engine, sampling, new NoOpAuditMetrics());
  }

  @BeforeEach
  void setUp() {
    events.clear();
  }

  @Test
  @DisplayName("GIVEN a matching path rule WHEN a reactive request completes THEN it is audited")
  void auditsMatchingPath() {
    buildFilter(propertiesFor("/api/**"));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/payments").build());
    WebFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    assertThat(events).hasSize(1);
    AuditLogRecord record = events.get(0).record();
    assertThat(record.getType()).isEqualTo("INCOMING");
    assertThat(record.getServiceName()).isEqualTo("reactive-svc");
    assertThat(record.getMethod()).isEqualTo("GET");
    assertThat(record.getDescription()).isEqualTo("Reactive path rule");
    assertThat(record.getCorrelationId()).isNotBlank();
  }

  @Test
  @DisplayName(
      "GIVEN a non-matching path WHEN a reactive request completes THEN nothing is captured")
  void skipsNonMatchingPath() {
    buildFilter(propertiesFor("/api/**"));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/health").build());
    WebFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName(
      "GIVEN a correlation header WHEN captured THEN it is reused and returned on the response")
  void propagatesCorrelationId() {
    buildFilter(propertiesFor("/api/**"));
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/x")
                .header("X-Correlation-ID", "corr-reactive-1")
                .build());
    WebFilterChain chain = ex -> Mono.empty();

    filter.filter(exchange, chain).block();

    assertThat(events).hasSize(1);
    assertThat(events.get(0).record().getCorrelationId()).isEqualTo("corr-reactive-1");
    assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"))
        .isEqualTo("corr-reactive-1");
  }
}
