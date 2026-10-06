package com.api.audit.storage.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.model.AuditLogRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link JsonLineAuditLogStore}.
 *
 * @author Puneet Swarup
 */
class JsonLineAuditLogStoreTest {

  @Test
  @DisplayName("GIVEN a file sink WHEN records are saved THEN one JSON line per record is written")
  void writesJsonLines(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("audit.log");
    JsonLineAuditLogStore store = new JsonLineAuditLogStore(file);

    store.save(
        AuditLogRecord.builder()
            .serviceName("svc")
            .type("INCOMING")
            .method("GET")
            .url("/a")
            .timestamp(LocalDateTime.now())
            .build());
    store.save(
        AuditLogRecord.builder()
            .serviceName("svc")
            .type("OUTGOING")
            .method("POST")
            .url("/b")
            .timestamp(LocalDateTime.now())
            .build());

    List<String> lines = Files.readAllLines(file);
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).contains("\"type\":\"INCOMING\"").contains("\"url\":\"/a\"");
    assertThat(lines.get(1)).contains("\"type\":\"OUTGOING\"");
  }

  @Test
  @DisplayName("GIVEN a nested path WHEN saving THEN parent directories are created")
  void createsParentDirectories(@TempDir Path dir) {
    Path file = dir.resolve("nested/deeper/audit.log");
    JsonLineAuditLogStore store = new JsonLineAuditLogStore(file);

    store.save(AuditLogRecord.builder().type("INCOMING").build());

    assertThat(Files.exists(file)).isTrue();
  }

  @Test
  @DisplayName("GIVEN stdout sink WHEN saving THEN no exception is thrown")
  void stdoutSinkWrites() {
    JsonLineAuditLogStore store = JsonLineAuditLogStore.toStdout();
    store.save(AuditLogRecord.builder().type("INCOMING").url("/x").build());
  }
}
