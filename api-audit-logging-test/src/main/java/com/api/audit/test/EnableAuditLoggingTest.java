package com.api.audit.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables audit-capture support in a Spring test.
 *
 * <p>Place on a {@code @SpringBootTest} class (or a slice test) to register a {@link
 * CapturedAuditLogs} bean that records every published audit event. Inject it to assert on captured
 * records.
 *
 * <p><b>Example</b>
 *
 * <pre>{@code
 * @SpringBootTest
 * @AutoConfigureMockMvc
 * @EnableAuditLoggingTest
 * class PaymentAuditTest {
 *
 *   @Autowired MockMvc mockMvc;
 *   @Autowired CapturedAuditLogs auditLogs;
 *
 *   @Test
 *   void capturesIncoming() {
 *     mockMvc.perform(get("/api/v1/payments/42"));
 *     auditLogs.single().hasType("INCOMING").hasMethod("GET").hasStatus(200);
 *   }
 * }
 * }</pre>
 *
 * @author Puneet Swarup
 * @see CapturedAuditLogs
 * @see AuditLogAssertions
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(AuditLogTestConfiguration.class)
public @interface EnableAuditLoggingTest {}
