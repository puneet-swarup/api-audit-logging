package com.api.audit.mask;

import com.api.audit.config.AuditLoggingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON-aware payload masker that redacts sensitive values by walking the parsed JSON tree.
 *
 * <p>This is the default {@link PayloadMasker}. Unlike a regular-expression approach, it:
 *
 * <ul>
 *   <li>correctly handles nested objects and arrays at any depth;
 *   <li>masks values of any JSON type (string, number, boolean, object, array), not just strings;
 *   <li>does not corrupt payloads whose values contain commas, braces, or quotes;
 *   <li>matches keys case-insensitively and by containment (so {@code cardNumber} matches {@code
 *       card});
 *   <li>leaves non-JSON payloads untouched (they are returned as-is).
 * </ul>
 *
 * <p><b>Masking value:</b> a sensitive key's value is replaced with a fixed sentinel string so that
 * downstream consumers can see that a field existed and was redacted without seeing the value.
 *
 * <p><b>Failure handling:</b> If the payload is not valid JSON, it is returned unchanged. This is a
 * deliberate choice for audit capture: bodies such as plain text, XML, or form data must not be
 * dropped or rejected, and the library must never throw on the async audit path. Callers that need
 * to guarantee redaction for non-JSON payloads should configure their own {@link PayloadMasker}.
 *
 * @author Puneet Swarup
 * @see PayloadMasker
 */
@Slf4j
public class JsonTreePayloadMasker implements PayloadMasker {

  /** The value written in place of a sensitive field's value. */
  public static final String MASK = "******";

  /**
   * Built-in sensitive keys — always masked, non-configurable. These cover the most common
   * compliance requirements (GDPR, PCI-DSS). Matching is case-insensitive and uses a
   * contains-check, so {@code card} matches {@code cardNumber} and {@code debitCard}.
   */
  private static final List<String> BUILT_IN_KEYS =
      List.of("password", "token", "cvv", "cardnumber", "secret", "authorization");

  private static final int DEFAULT_MAX_DEPTH = 64;

  private final ObjectMapper objectMapper;
  private final Set<String> sensitiveKeysLower;
  private final int maxDepth;

  /**
   * Creates the masker from configuration, merging built-in keys with any configured additional
   * fields. Keys are normalised to lower case for case-insensitive matching.
   *
   * @param properties the library configuration properties; never {@code null}
   */
  public JsonTreePayloadMasker(AuditLoggingProperties properties) {
    this(new ObjectMapper(), properties.getMasking().getAdditionalFields(), DEFAULT_MAX_DEPTH);
  }

  /**
   * Creates the masker with an explicit object mapper, additional keys, and depth limit. Intended
   * for tests and for callers that want to control the parser configuration.
   *
   * @param objectMapper the Jackson mapper to use; never {@code null}
   * @param additionalFields extra sensitive key names; may be {@code null}
   * @param maxDepth maximum tree depth to walk before masking is skipped for deeper nodes
   */
  public JsonTreePayloadMasker(
      ObjectMapper objectMapper, List<String> additionalFields, int maxDepth) {
    this.objectMapper = objectMapper;
    this.maxDepth = maxDepth <= 0 ? DEFAULT_MAX_DEPTH : maxDepth;

    Set<String> keys = new LinkedHashSet<>(BUILT_IN_KEYS);
    if (additionalFields != null) {
      for (String field : additionalFields) {
        if (field != null && !field.isBlank()) {
          keys.add(field.toLowerCase(Locale.ROOT));
        }
      }
    }
    this.sensitiveKeysLower = keys;
  }

  @Override
  public String mask(String payload) {
    if (payload == null) {
      return null;
    }
    String trimmed = payload.strip();
    if (trimmed.isEmpty()) {
      return payload;
    }
    char first = trimmed.charAt(0);
    // Only attempt JSON parsing when the payload plausibly looks like a JSON object or array.
    if (first != '{' && first != '[') {
      return payload;
    }
    try {
      JsonNode root = objectMapper.readTree(trimmed);
      maskNode(root, 0);
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException ex) {
      // Not valid JSON, or cannot be re-serialized. Leave the payload untouched rather than fail.
      log.debug("[AuditLog] Payload is not valid JSON; leaving it unmasked: {}", ex.getMessage());
      return payload;
    }
  }

  private void maskNode(JsonNode node, int depth) {
    if (node == null || depth > maxDepth) {
      return;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      // Collect field names first to avoid concurrent modification while replacing.
      Set<String> fieldNames = new LinkedHashSet<>();
      object.fieldNames().forEachRemaining(fieldNames::add);
      for (String fieldName : fieldNames) {
        if (isSensitive(fieldName)) {
          object.set(fieldName, TextNode.valueOf(MASK));
        } else {
          maskNode(object.get(fieldName), depth + 1);
        }
      }
    } else if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (int i = 0; i < array.size(); i++) {
        maskNode(array.get(i), depth + 1);
      }
    }
  }

  /**
   * Returns whether a field name is sensitive. Matching is case-insensitive and uses a contains
   * check so that compound names such as {@code cardNumber} are caught by the {@code card} token.
   *
   * @param fieldName the JSON field name; may be {@code null}
   * @return {@code true} when the field's value must be masked
   */
  private boolean isSensitive(String fieldName) {
    if (fieldName == null) {
      return false;
    }
    String lower = fieldName.toLowerCase(Locale.ROOT);
    for (String key : sensitiveKeysLower) {
      if (lower.contains(key)) {
        return true;
      }
    }
    return false;
  }
}
