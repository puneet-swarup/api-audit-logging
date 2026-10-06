package com.api.audit.webclient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;

/**
 * Helpers for handling reactive bodies on the audit path.
 *
 * <p>Reactive bodies are single-subscription. This class provides the primitives to join a list of
 * {@link DataBuffer}s into bytes (releasing each buffer) and to convert bytes into the string
 * stored on an audit record, applying a size limit. It has no knowledge of audit records or
 * policies, which keeps it small, testable, and reusable.
 *
 * @author Puneet Swarup
 */
public final class WebClientBodyCapture {

  private final int maxBodyBytes;

  /**
   * Creates the helper.
   *
   * @param maxBodyBytes maximum bytes to retain for the audit record; values below 1 fall back to 1
   *     MiB
   */
  public WebClientBodyCapture(int maxBodyBytes) {
    this.maxBodyBytes = maxBodyBytes < 1 ? 1024 * 1024 : maxBodyBytes;
  }

  /**
   * Joins a list of data buffers into a single byte array and releases each buffer.
   *
   * @param buffers the buffers to join; never {@code null}
   * @return the concatenated bytes; empty array when there are no buffers
   */
  public static byte[] join(List<DataBuffer> buffers) {
    int total = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
    byte[] out = new byte[total];
    int offset = 0;
    for (DataBuffer buffer : buffers) {
      int len = buffer.readableByteCount();
      buffer.read(out, offset, len);
      offset += len;
      DataBufferUtils.release(buffer);
    }
    return out;
  }

  /**
   * Converts body bytes to the string stored on an audit record, applying the size limit.
   *
   * @param bytes the body bytes; never {@code null}
   * @return the body string, or a truncation marker when the body is larger than the limit
   */
  public String toCapturedString(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    if (bytes.length > maxBodyBytes) {
      return "[BODY TRUNCATED: " + bytes.length + " bytes, limit " + maxBodyBytes + "]";
    }
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
