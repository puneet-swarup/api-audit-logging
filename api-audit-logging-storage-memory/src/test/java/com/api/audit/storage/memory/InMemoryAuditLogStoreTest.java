package com.api.audit.storage.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import com.api.audit.spi.AuditLogSearchStore;
import com.api.audit.spi.AuditLogStore;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Unit tests for the in-memory audit store using the framework-neutral query API.
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
            .tags(Map.of("module", "demo"))
            .timestamp(LocalDateTime.now())
            .build());

    AuditLogPage<AuditLogRecord> page =
        store.search(
            AuditLogQuery.builder()
                .correlationId("cid-1")
                .type("INCOMING")
                .url("hell")
                .serviceName("demo")
                .method("GET")
                .httpStatus(200)
                .clientIp("203.0.113.10")
                .principalName("puneet")
                .tagKey("module")
                .tagValue("demo")
                .page(0)
                .size(10)
                .build());

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().get(0).getUrl()).isEqualTo("/hello");
    assertThat(page.getContent().get(0).getTags()).containsEntry("module", "demo");
  }

  @Test
  void tagKeyFilterMatchesRecordsCarryingTheTag() {
    InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    store.save(
        AuditLogRecord.builder()
            .type("INCOMING")
            .method("GET")
            .url("/tagged")
            .tags(Map.of("module", "payments", "tier", "critical"))
            .timestamp(LocalDateTime.now())
            .build());
    store.save(
        AuditLogRecord.builder()
            .type("INCOMING")
            .method("GET")
            .url("/untagged")
            .timestamp(LocalDateTime.now())
            .build());

    var byKey =
        store.search(AuditLogQuery.builder().type("INCOMING").tagKey("module").size(10).build());
    assertThat(byKey.getTotalElements()).isEqualTo(1);
    assertThat(byKey.getContent().get(0).getUrl()).isEqualTo("/tagged");

    var byKeyAndValue =
        store.search(
            AuditLogQuery.builder()
                .type("INCOMING")
                .tagKey("tier")
                .tagValue("critical")
                .size(10)
                .build());
    assertThat(byKeyAndValue.getTotalElements()).isEqualTo(1);

    var byKeyWrongValue =
        store.search(
            AuditLogQuery.builder()
                .type("INCOMING")
                .tagKey("tier")
                .tagValue("low")
                .size(10)
                .build());
    assertThat(byKeyWrongValue.getTotalElements()).isZero();
  }

  @Test
  void paginationAndSortingAreHonored() {
    InMemoryAuditLogStore store = new InMemoryAuditLogStore();
    for (int i = 0; i < 5; i++) {
      store.save(
          AuditLogRecord.builder()
              .type("INCOMING")
              .url("/item/" + i)
              .timestamp(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(i))
              .build());
    }

    var firstPage =
        store.search(AuditLogQuery.builder().size(2).page(0).sortAscending(true).build());
    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(5);
    assertThat(firstPage.getTotalPages()).isEqualTo(3);
    assertThat(firstPage.getContent().get(0).getUrl()).isEqualTo("/item/0");

    var descending =
        store.search(AuditLogQuery.builder().size(2).page(0).sortAscending(false).build());
    assertThat(descending.getContent().get(0).getUrl()).isEqualTo("/item/4");
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
