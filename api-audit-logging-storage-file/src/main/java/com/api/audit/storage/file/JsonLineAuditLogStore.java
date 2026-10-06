package com.api.audit.storage.file;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.spi.AuditLogStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes audit records as one JSON object per line (JSON Lines) to a file or to standard output.
 *
 * <p>This sink is for local development, containers, and lightweight SRE setups: records go to a
 * rotating-friendly file or to stdout where the container runtime collects them. It is a write-only
 * sink and does not implement search.
 *
 * <p><b>Thread safety.</b> Writes are synchronized so records from the audit executor do not
 * interleave. Each call appends one complete line and flushes, so the file is durable enough for
 * tailing with {@code tail -f}.
 *
 * <p><b>Failure handling.</b> A write failure is logged and swallowed. Like all stores, this must
 * not propagate exceptions to the audit path.
 *
 * @author Puneet Swarup
 */
@Slf4j
public class JsonLineAuditLogStore implements AuditLogStore {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final Path file;
  private final Writer stdout;
  private final Object lock = new Object();

  /**
   * Creates a store that appends JSON lines to the given file.
   *
   * @param file the destination file; parent directories are created if missing
   */
  public JsonLineAuditLogStore(Path file) {
    this.file = file;
    this.stdout = null;
  }

  private JsonLineAuditLogStore(Writer stdout) {
    this.file = null;
    this.stdout = stdout;
  }

  /**
   * Creates a store that writes JSON lines to standard output.
   *
   * @return a stdout-backed store
   */
  public static JsonLineAuditLogStore toStdout() {
    return new JsonLineAuditLogStore(
        new java.io.OutputStreamWriter(System.out, StandardCharsets.UTF_8));
  }

  @Override
  public void save(AuditLogRecord record) {
    String line;
    try {
      line = MAPPER.writeValueAsString(record);
    } catch (JsonProcessingException ex) {
      log.warn("[AuditLog] Could not serialize audit record to JSON: {}", ex.getMessage());
      return;
    }

    synchronized (lock) {
      try {
        if (stdout != null) {
          stdout.write(line);
          stdout.write(System.lineSeparator());
          stdout.flush();
        } else {
          if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
          }
          Files.writeString(
              file,
              line + System.lineSeparator(),
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND);
        }
      } catch (IOException ex) {
        log.error("[AuditLog] Failed to write audit record: {}", ex.getMessage());
      }
    }
  }
}
