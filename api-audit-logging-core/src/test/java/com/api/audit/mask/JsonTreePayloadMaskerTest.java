package com.api.audit.mask;

import static org.assertj.core.api.Assertions.assertThat;

import com.api.audit.config.AuditLoggingProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonTreePayloadMasker}.
 *
 * <p>These tests prove the correctness properties that the previous regex-based masker could not
 * provide: nested objects, arrays, non-string value types, escaped quotes, and keys that merely
 * contain a sensitive token.
 *
 * @author Puneet Swarup
 */
class JsonTreePayloadMaskerTest {

  private JsonTreePayloadMasker defaultMasker() {
    return new JsonTreePayloadMasker(new AuditLoggingProperties());
  }

  private JsonTreePayloadMasker maskerWithAdditional(String... fields) {
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getMasking().setAdditionalFields(List.of(fields));
    return new JsonTreePayloadMasker(props);
  }

  @Test
  @DisplayName("GIVEN null WHEN masking THEN null is returned")
  void nullReturnsNull() {
    assertThat(defaultMasker().mask(null)).isNull();
  }

  @Test
  @DisplayName("GIVEN built-in sensitive keys WHEN masking THEN values are redacted")
  void masksBuiltInKeys() {
    String input = "{\"password\":\"secret123\",\"token\":\"abc-123\",\"other\":\"data\"}";
    String result = defaultMasker().mask(input);
    assertThat(result).contains("\"password\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"token\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"other\":\"data\"");
  }

  @Test
  @DisplayName("GIVEN mixed-case keys WHEN masking THEN matching is case-insensitive")
  void matchingIsCaseInsensitive() {
    String result = defaultMasker().mask("{\"PASSWORD\":\"secret\"}");
    assertThat(result).contains("\"PASSWORD\":\"" + JsonTreePayloadMasker.MASK + "\"");
  }

  @Test
  @DisplayName("GIVEN nested objects WHEN masking THEN sensitive keys at any depth are redacted")
  void masksNestedObjects() {
    String input =
        "{\"user\":{\"name\":\"Puneet\",\"credentials\":{\"password\":\"secret\"}},\"id\":1}";
    String result = defaultMasker().mask(input);
    assertThat(result).contains("\"password\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"name\":\"Puneet\"");
    assertThat(result).doesNotContain("secret");
  }

  @Test
  @DisplayName("GIVEN arrays WHEN masking THEN sensitive keys inside array elements are redacted")
  void masksInsideArrays() {
    String input = "[{\"password\":\"a\"},{\"password\":\"b\"},{\"safe\":\"c\"}]";
    String result = defaultMasker().mask(input);
    assertThat(result).doesNotContain("\"a\"").doesNotContain("\"b\"");
    assertThat(result).contains("\"safe\":\"c\"");
  }

  @Test
  @DisplayName("GIVEN numeric/boolean sensitive values WHEN masking THEN value is redacted")
  void masksNonStringValues() {
    String input = "{\"password\":12345,\"token\":true,\"cvv\":null}";
    String result = defaultMasker().mask(input);
    assertThat(result).contains("\"password\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"token\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"cvv\":\"" + JsonTreePayloadMasker.MASK + "\"");
  }

  @Test
  @DisplayName("GIVEN values with commas/braces/quotes WHEN masking THEN payload is not corrupted")
  void doesNotCorruptComplexValues() {
    String input = "{\"note\":\"a,b{c}\\\"d\",\"password\":\"x\"}";
    String result = defaultMasker().mask(input);
    assertThat(result).contains("a,b{c}\\\"d");
    assertThat(result).contains("\"password\":\"" + JsonTreePayloadMasker.MASK + "\"");
  }

  @Test
  @DisplayName("GIVEN key containing a sensitive token WHEN masking THEN it is redacted")
  void masksCompoundKeyNames() {
    String input = "{\"cardNumber\":\"4111111111111111\"}";
    String result = defaultMasker().mask(input);
    assertThat(result).contains("\"cardNumber\":\"" + JsonTreePayloadMasker.MASK + "\"");
  }

  @Test
  @DisplayName("GIVEN additional configured fields WHEN masking THEN they are also redacted")
  void masksAdditionalConfiguredFields() {
    String input = "{\"otp\":\"123456\",\"nationalId\":\"AB123\",\"name\":\"Puneet\"}";
    String result = maskerWithAdditional("otp", "nationalId").mask(input);
    assertThat(result).contains("\"otp\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"nationalId\":\"" + JsonTreePayloadMasker.MASK + "\"");
    assertThat(result).contains("\"name\":\"Puneet\"");
  }

  @Test
  @DisplayName("GIVEN non-JSON payload WHEN masking THEN it is returned unchanged")
  void nonJsonIsReturnedUnchanged() {
    String plain = "this is not json";
    assertThat(defaultMasker().mask(plain)).isEqualTo(plain);
  }

  @Test
  @DisplayName("GIVEN malformed JSON WHEN masking THEN it is returned unchanged and no exception")
  void malformedJsonIsReturnedUnchanged() {
    String malformed = "{\"password\": ";
    assertThat(defaultMasker().mask(malformed)).isEqualTo(malformed);
  }

  @Test
  @DisplayName("GIVEN empty object WHEN masking THEN returns a valid empty object")
  void emptyObjectIsHandled() {
    assertThat(defaultMasker().mask("{}")).isEqualTo("{}");
  }
}
