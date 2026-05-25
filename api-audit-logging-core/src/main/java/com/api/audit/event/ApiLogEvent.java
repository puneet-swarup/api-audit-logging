package com.api.audit.event;

import com.api.audit.model.AuditLogRecord;

/**
 * A decoupled event carrier used to transport audit data through the Spring application event bus
 * for asynchronous persistence.
 *
 * <p>Published by capture components ({@link com.api.audit.filter.IncomingLoggingFilter}, {@link
 * com.api.audit.feign.CustomFeignLogger}, WebClient filter, RestTemplate interceptor) and consumed
 * by {@link com.api.audit.listener.ApiLogListener}.
 *
 * <p>Using a Java {@code record} ensures the event is immutable — no component can modify the
 * captured data after it has been published.
 *
 * @param record the fully assembled audit record ready for masking and persistence
 * @author Puneet Swarup
 */
public record ApiLogEvent(AuditLogRecord record) {}
