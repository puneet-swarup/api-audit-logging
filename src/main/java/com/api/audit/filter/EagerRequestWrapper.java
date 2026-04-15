package com.api.audit.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import org.springframework.util.StreamUtils;

/**
 * An {@link HttpServletRequestWrapper} that eagerly reads the request input stream and caches it in
 * memory.
 *
 * <p>Standard {@link jakarta.servlet.http.HttpServletRequest} input streams can only be read once.
 * This wrapper solves that limitation by capturing the entire body during initialization, allowing
 * multiple subsequent reads by logging filters, security filters, and Spring Controllers.
 *
 * <p>In a 2026 Spring Boot 3.x environment, this is particularly useful for capturing POST bodies
 * before they are consumed by Jackson or Form Parameter parsers.
 *
 * @author Puneet Swarup
 */
public class EagerRequestWrapper extends HttpServletRequestWrapper {
  private final byte[] cachedBody;

  /**
   * Constructs a request wrapper that immediately caches the request body.
   *
   * @param request The original {@link HttpServletRequest}.
   * @throws IOException If an input or output error occurs while reading the stream.
   */
  public EagerRequestWrapper(HttpServletRequest request) throws IOException {
    super(request);
    // Eagerly read the stream into a byte array
    this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
  }

  /**
   * Returns a new {@link ServletInputStream} based on the cached byte array.
   *
   * @return A re-readable {@link ServletInputStream}.
   */
  @Override
  public ServletInputStream getInputStream() {
    return new CachedServletInputStream(this.cachedBody);
  }

  /**
   * Returns a {@link BufferedReader} for the cached body.
   *
   * @return A {@link BufferedReader} representing the request body.
   */
  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody)));
  }

  /**
   * Provides access to the raw cached body bytes.
   *
   * @return The request body as a byte array.
   */
  public byte[] getBody() {
    return this.cachedBody;
  }

  /**
   * Implementation of {@link ServletInputStream} that reads from a {@link ByteArrayInputStream}
   * instead of the physical socket.
   */
  private static class CachedServletInputStream extends ServletInputStream {
    private final InputStream cachedInputStream;

    /**
     * Initializes the stream with the provided byte array.
     *
     * @param cachedBody The byte array to be streamed.
     */
    public CachedServletInputStream(byte[] cachedBody) {
      this.cachedInputStream = new ByteArrayInputStream(cachedBody);
    }

    @Override
    public int read() throws IOException {
      return cachedInputStream.read();
    }

    /**
     * Checks if the stream has reached the end of the cached body.
     *
     * @return {@code true} if no more data is available to read.
     */
    @Override
    public boolean isFinished() {
      try {
        return cachedInputStream.available() == 0;
      } catch (IOException e) {
        throw new UncheckedIOException("Error checking stream availability", e);
      }
    }

    /**
     * Indicates the stream is ready for reading (always returns true for cached data).
     *
     * @return {@code true}
     */
    @Override
    public boolean isReady() {
      return true;
    }

    /**
     * Read listeners are not required for this blocking cached implementation.
     *
     * @param readListener The listener to be set.
     */
    @Override
    public void setReadListener(ReadListener readListener) {}
  }
}
