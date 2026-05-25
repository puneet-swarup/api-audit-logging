package com.api.audit.storage.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for the in-memory audit store.
 *
 * @author Puneet Swarup
 */
class InMemoryAuditLogStoreTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MemoryAuditLogAutoConfiguration.class));

  @Test
  void saveAndSearchReturnsMatchingRecords() {
    InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    store.save(
        AuditLogRecord.builder()
            .serviceName("demo")
            .type("INCOMING")
            .method("GET")
            .httpStatus(200)
            .url("/hello")
            .correlationId("cid-1")
            .clientIp("203.0.113.10")
            .principalName("puneet")
            .timestamp(LocalDateTime.now())
            .build());

    var page =
        store.search(
            "cid-1",
            null,
            null,
            "INCOMING",
            "hell",
            "demo",
            "GET",
            200,
            "203.0.113.10",
            "puneet",
            null,
            PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().get(0).getUrl()).isEqualTo("/hello");
  }

  @Test
  void autoConfigurationRegistersStoreOnlyWhenMemoryStorageSelected() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(InMemoryAuditLogStore.class));

    contextRunner
        .withPropertyValues("audit.logging.storage.type=memory")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InMemoryAuditLogStore.class);
              assertThat(context).hasSingleBean(AuditLogStore.class);
              assertThat(context).hasSingleBean(AuditLogSearchStore.class);
            });

    contextRunner
        .withPropertyValues("audit.logging.storage.type=jdbc")
        .run(context -> assertThat(context).doesNotHaveBean(InMemoryAuditLogStore.class));
  }
}
