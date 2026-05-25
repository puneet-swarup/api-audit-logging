package com.api.audit.resttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.context.CorrelationContext;
import com.api.audit.event.ApiLogEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Verifies Spring HTTP interface clients built on RestClient are covered by the same audit path.
 *
 * <p>Spring's {@code @HttpExchange} proxies delegate the actual HTTP call to the configured {@link
 * RestClient}. That means applications get audit logging as long as they build the client through a
 * Boot-managed {@link RestClient.Builder}; no separate proxy-specific integration is needed.
 *
 * @author Puneet Swarup
 */
class HttpInterfaceRestClientAuditTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void capturesHttpInterfaceCallsWhenProxyUsesAuditedRestClient() {
    CapturingPublisher publisher = new CapturingPublisher();
    RestClient.Builder builder = RestClient.builder().baseUrl("https://inventory.example.org");
    new RestTemplateAuditAutoConfiguration()
        .auditRestClientCustomizer(publisher, new AuditLoggingProperties())
        .customize(builder);

    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(once(), requestTo("https://inventory.example.org/items/sku-123?expand=stock"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(CorrelationContext.CORRELATION_ID_HEADER, "corr-http-interface-1"))
        .andRespond(withSuccess("{\"available\":true}", MediaType.APPLICATION_JSON));

    InventoryClient client =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build()
            .createClient(InventoryClient.class);

    MDC.put(CorrelationContext.CORRELATION_ID_HEADER, "corr-http-interface-1");
    String response = client.getItem("sku-123");

    server.verify();
    assertThat(response).contains("available");
    assertThat(publisher.apiLogEvents()).hasSize(1);

    ApiLogEvent event = publisher.apiLogEvents().getFirst();
    assertThat(event.record().getType()).isEqualTo("OUTGOING");
    assertThat(event.record().getMethod()).isEqualTo("GET");
    assertThat(event.record().getUrl())
        .isEqualTo("https://inventory.example.org/items/sku-123?expand=stock");
    assertThat(event.record().getQueryString()).isEqualTo("expand=stock");
    assertThat(event.record().getHttpStatus()).isEqualTo(200);
    assertThat(event.record().getCorrelationId()).isEqualTo("corr-http-interface-1");
  }

  @HttpExchange
  interface InventoryClient {

    @GetExchange("/items/{sku}?expand=stock")
    String getItem(@PathVariable("sku") String sku);
  }

  static class CapturingPublisher implements ApplicationEventPublisher {

    private final List<ApiLogEvent> apiLogEvents = new ArrayList<>();

    @Override
    public void publishEvent(ApplicationEvent event) {
      publishEvent((Object) event);
    }

    @Override
    public void publishEvent(Object event) {
      if (event instanceof ApiLogEvent apiLogEvent) {
        apiLogEvents.add(apiLogEvent);
      }
    }

    List<ApiLogEvent> apiLogEvents() {
      return apiLogEvents;
    }
  }
}
