package com.api.audit.samples.demoApp;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.controller.ApiLogController;
import com.api.audit.spi.AuditLogStore;
import com.api.audit.storage.jdbc.JdbcAuditLogStore;
import com.api.audit.storage.jpa.JpaAuditLogStore;
import com.api.audit.storage.kafka.KafkaAuditLogStore;
import com.api.audit.storage.memory.InMemoryAuditLogStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke tests for the demo application's storage profiles.
 *
 * <p>The demo app is meant to be more than a runnable sample; it should also be a reference for
 * developers comparing storage options. These tests keep the profiles honest by verifying that each
 * profile starts and selects the intended sink.
 *
 * @author Puneet Swarup
 */
class DemoProfileContextTest {}

/** Verifies the default JPA-style demo profile. */
@SpringBootTest
@ActiveProfiles("jpa")
class JpaDemoProfileContextTest {

  @Autowired private AuditLogStore auditLogStore;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void startsWithJpaAuditStoreAndSearchEndpoint() {
    assertThat(auditLogStore).isInstanceOf(JpaAuditLogStore.class);
    assertThat(applicationContext.getBeansOfType(ApiLogController.class)).hasSize(1);
  }
}

/** Verifies the JdbcTemplate demo profile. */
@SpringBootTest
@ActiveProfiles("jdbc")
class JdbcDemoProfileContextTest {

  @Autowired private AuditLogStore auditLogStore;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void startsWithJdbcAuditStoreAndSearchEndpoint() {
    assertThat(auditLogStore).isInstanceOf(JdbcAuditLogStore.class);
    assertThat(applicationContext.getBeansOfType(ApiLogController.class)).hasSize(1);
  }
}

/** Verifies the in-memory demo profile. */
@SpringBootTest
@ActiveProfiles("memory")
class MemoryDemoProfileContextTest {

  @Autowired private AuditLogStore auditLogStore;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void startsWithMemoryAuditStoreAndSearchEndpoint() {
    assertThat(auditLogStore).isInstanceOf(InMemoryAuditLogStore.class);
    assertThat(applicationContext.getBeansOfType(ApiLogController.class)).hasSize(1);
  }
}

/** Verifies the Kafka streaming demo profile. */
@SpringBootTest
@ActiveProfiles("kafka")
class KafkaDemoProfileContextTest {

  @Autowired private AuditLogStore auditLogStore;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void startsWithKafkaAuditStoreWithoutSearchEndpoint() {
    assertThat(auditLogStore).isInstanceOf(KafkaAuditLogStore.class);
    assertThat(applicationContext.getBeansOfType(ApiLogController.class)).isEmpty();
  }
}
