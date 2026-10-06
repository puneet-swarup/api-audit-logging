package com.api.audit.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;

/**
 * Tests that the native-image runtime hints register the audit model types for reflection.
 *
 * @author Puneet Swarup
 */
class AuditLogRuntimeHintsTest {

  @Test
  @DisplayName("GIVEN the registrar WHEN hints are built THEN the model types are registered")
  void registersModelTypes() {
    RuntimeHints hints = new RuntimeHints();
    new AuditLogRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(hints.reflection().getTypeHint(AuditLogRecord.class)).isNotNull();
    assertThat(hints.reflection().getTypeHint(AuditLogQuery.class)).isNotNull();
    assertThat(hints.reflection().getTypeHint(AuditLogPage.class)).isNotNull();
  }
}
