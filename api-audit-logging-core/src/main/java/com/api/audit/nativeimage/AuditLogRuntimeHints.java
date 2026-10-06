package com.api.audit.nativeimage;

import com.api.audit.model.AuditLogRecord;
import com.api.audit.query.AuditLogPage;
import com.api.audit.query.AuditLogQuery;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native-image runtime hints for the audit model types.
 *
 * <p>Spring Boot's AOT engine already handles most of the library's native-image needs
 * (configuration properties binding, auto-configuration, conditional evaluation). This registrar
 * covers the model types that are serialized/deserialized reflectively by Jackson — most
 * importantly {@link AuditLogRecord}, which the Kafka and file sinks serialize, and the search
 * types returned by the internal endpoint.
 *
 * <p>Registering reflection here means an application can build a native image without adding hints
 * of its own for the audit model.
 *
 * @author Puneet Swarup
 */
public class AuditLogRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    registerForJackson(hints, AuditLogRecord.class);
    registerForJackson(hints, AuditLogQuery.class);
    registerForJackson(hints, AuditLogPage.class);
  }

  private void registerForJackson(RuntimeHints hints, Class<?> type) {
    hints
        .reflection()
        .registerType(
            type,
            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
            MemberCategory.INVOKE_DECLARED_METHODS,
            MemberCategory.DECLARED_FIELDS);
  }
}
