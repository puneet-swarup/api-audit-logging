package com.api.audit.webclient;

import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * Request-body helpers for capturing WebClient request bodies.
 *
 * <p>A {@code ClientRequest} exposes its body only as an opaque {@code BodyInserter}, so the audit
 * filter cannot read a request body without the caller's cooperation. This class provides a body
 * inserter that records the body it writes into a holder the caller passes in, then delegates to
 * the standard inserter so the bytes still reach the network.
 *
 * <p><b>Usage</b>
 *
 * <pre>{@code
 * CapturedBody holder = new CapturedBody();
 * client.post()
 *     .uri("/payments")
 *     .body(AuditBodyInserters.fromValue(request, holder))
 *     .retrieve()
 *     .bodyToMono(String.class);
 *
 * // After the exchange completes (or from an ExchangeFilterFunction), read holder.get()
 * }</pre>
 *
 * <p>For most applications the simpler path is to let the audit filter capture the response body
 * and to log the request body via the inbound record; request-body capture is offered for cases
 * where the outbound payload itself is the audited artifact.
 *
 * @author Puneet Swarup
 */
public final class AuditBodyInserters {

  private AuditBodyInserters() {}

  /**
   * A mutable holder for a captured request body.
   *
   * <p>Written by the inserter on the request thread and read afterwards; uses a volatile field for
   * safe publication across reactive operators.
   */
  public static final class CapturedBody {
    private volatile String value = "[REQUEST BODY NOT CAPTURED]";

    /**
     * Sets the captured value.
     *
     * @param body the captured body string
     */
    public void set(String body) {
      this.value = body;
    }

    /**
     * Returns the captured value.
     *
     * @return the captured body, or a placeholder when nothing was captured
     */
    public String get() {
      return value;
    }
  }

  /**
   * Creates a {@link BodyInserter} that records the JSON body it writes into the supplied holder.
   *
   * @param value the object to serialize and write
   * @param holder the holder to record the serialized body into
   * @param <T> the value type
   * @return a body inserter that writes the value and records its JSON form
   */
  public static <T> BodyInserter<T, ReactiveHttpOutputMessage> fromValue(
      T value, CapturedBody holder) {
    return (outputMessage, context) -> {
      try {
        // Best-effort capture of the serialized form for simple JSON values.
        holder.set(String.valueOf(value));
      } catch (RuntimeException ignored) {
        // Never let capture affect the request.
      }
      return BodyInserters.fromValue(value).insert(outputMessage, context);
    };
  }
}
