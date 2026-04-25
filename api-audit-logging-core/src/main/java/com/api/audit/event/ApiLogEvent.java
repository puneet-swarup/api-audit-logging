package com.api.audit.event;

import com.api.audit.entity.ApiAuditLog;

/**
 * A decoupled event carrier used to transport audit data across the application context.
 *
 * <p>This record is published by interceptors (such as {@code IncomingLoggingFilter} or {@code
 * CustomFeignLogger}) and is typically consumed by an asynchronous event listener. Using an
 * event-driven approach ensures that the primary request thread is not blocked by database I/O
 * operations required for audit persistence.
 *
 * @param log the {@link ApiAuditLog} entity containing the captured transaction metadata and
 *     payloads
 * @author Puneet Swarup
 * @see org.springframework.context.ApplicationEventPublisher
 * @see ApiAuditLog
 */
public record ApiLogEvent(ApiAuditLog log) {}
