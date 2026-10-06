package com.api.audit.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the API Audit Logging examples application.
 *
 * <p>This module is a catalogue of runnable, self-contained examples. Each example lives in its own
 * class under a package that names the feature it demonstrates, so a reader can open exactly one
 * file to understand one capability. Examples are grouped as:
 *
 * <ul>
 *   <li>{@code annotation} — capturing via {@code @AuditLog}
 *   <li>{@code pathbased} — zero-code-change path auditing and every rule option
 *   <li>{@code masking} — payload masking and custom maskers
 *   <li>{@code capture} — capture modes (FULL / METADATA_ONLY / BODY_ONLY)
 *   <li>{@code policy} — custom {@code AuditPolicy} beans
 *   <li>{@code observability} — metrics and sampling
 * </ul>
 *
 * <p>Every example is activated by a Spring profile documented in
 * {@code src/main/resources/application-*.yaml} and in the module README.
 *
 * @author Puneet Swarup
 */
@SpringBootApplication
@EnableFeignClients
public class AuditExamplesApplication {

  /**
   * Boots the examples application.
   *
   * @param args standard Spring Boot arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(AuditExamplesApplication.class, args);
  }
}
