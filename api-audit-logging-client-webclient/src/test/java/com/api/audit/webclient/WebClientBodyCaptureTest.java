package com.api.audit.webclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;

/**
 * Unit tests for {@link WebClientBodyCapture}.
 *
 * @author Puneet Swarup
 */
class WebClientBodyCaptureTest {

  private static DataBuffer buffer(String s) {
    return DefaultDataBufferFactory.sharedInstance.wrap(s.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("GIVEN multiple buffers WHEN joined THEN bytes are concatenated")
  void joinsBuffers() {
    byte[] joined = WebClientBodyCapture.join(List.of(buffer("hello "), buffer("world")));
    assertThat(new String(joined, StandardCharsets.UTF_8)).isEqualTo("hello world");
  }

  @Test
  @DisplayName("GIVEN no buffers WHEN joined THEN empty array")
  void joinsEmpty() {
    assertThat(WebClientBodyCapture.join(List.of())).isEmpty();
  }

  @Test
  @DisplayName("GIVEN a small body WHEN converted THEN the raw string is returned")
  void convertsSmallBody() {
    WebClientBodyCapture capture = new WebClientBodyCapture(1024);
    assertThat(capture.toCapturedString("hello".getBytes(StandardCharsets.UTF_8)))
        .isEqualTo("hello");
  }

  @Test
  @DisplayName("GIVEN an oversized body WHEN converted THEN a truncation marker is returned")
  void truncatesOversizedBody() {
    WebClientBodyCapture capture = new WebClientBodyCapture(4);
    String result = capture.toCapturedString("1234567890".getBytes(StandardCharsets.UTF_8));
    assertThat(result).startsWith("[BODY TRUNCATED: 10 bytes");
  }

  @Test
  @DisplayName("GIVEN null/empty bytes WHEN converted THEN empty string")
  void handlesEmpty() {
    WebClientBodyCapture capture = new WebClientBodyCapture(1024);
    assertThat(capture.toCapturedString(null)).isEmpty();
    assertThat(capture.toCapturedString(new byte[0])).isEmpty();
  }
}
