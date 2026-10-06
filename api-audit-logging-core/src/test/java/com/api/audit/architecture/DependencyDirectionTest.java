package com.api.audit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lightweight architecture guardrails that assert dependency direction.
 *
 * <p>The audit core must not depend on any storage or client integration module; those are plug-ins
 * that depend on the core, never the other way around. This test scans the core sources and fails
 * if a forbidden import appears. It is intentionally dependency-free (no ArchUnit) so it runs
 * everywhere with no extra cost.
 *
 * @author Puneet Swarup
 */
class DependencyDirectionTest {

  private static final Path CORE_SRC = Path.of("src", "main", "java");

  private static final List<String> FORBIDDEN_PACKAGES =
      List.of(
          "com.api.audit.storage.",
          "com.api.audit.feign.",
          "com.api.audit.resttemplate.",
          "com.api.audit.webclient.");

  @Test
  @DisplayName(
      "GIVEN the core module WHEN scanned THEN it does not import storage or client modules")
  void coreDoesNotDependOnIntegrations() throws IOException {
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(CORE_SRC)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        for (String forbidden : FORBIDDEN_PACKAGES) {
          if (content.contains("import " + forbidden)) {
            violations.add(file + " -> " + forbidden);
          }
        }
      }
    }

    assertThat(violations)
        .as("The audit core must not depend on storage or client integration modules")
        .isEmpty();
  }
}
