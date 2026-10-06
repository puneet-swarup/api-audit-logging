package com.api.audit.storage.file;

import com.api.audit.config.AuditLoggingProperties;
import com.api.audit.spi.AuditLogStore;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the JSON-lines file / stdout audit sink.
 *
 * <p>Activated when {@code audit.logging.storage.type} is {@code file} (writes to a file) or {@code
 * stdout} (writes to standard output). The file location is configured with {@code
 * audit.logging.file.path}.
 *
 * @author Puneet Swarup
 * @see JsonLineAuditLogStore
 */
@AutoConfiguration(afterName = "com.api.audit.config.LoggingAutoConfiguration")
@EnableConfigurationProperties(AuditLoggingProperties.class)
@ConditionalOnExpression(
    "'${audit.logging.enabled:true}' == 'true' and "
        + "('${audit.logging.storage.type:}' == 'file' or '${audit.logging.storage.type:}' == 'stdout')")
public class FileAuditLogAutoConfiguration {

  /**
   * Registers the file or stdout store.
   *
   * @param properties the library configuration properties
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(AuditLogStore.class)
  public AuditLogStore fileAuditLogStore(AuditLoggingProperties properties) {
    String type = properties.getStorage().getType();
    if ("stdout".equalsIgnoreCase(type)) {
      return JsonLineAuditLogStore.toStdout();
    }
    String path = properties.getFile().getPath();
    return new JsonLineAuditLogStore(Path.of(path == null || path.isBlank() ? "audit.log" : path));
  }
}
