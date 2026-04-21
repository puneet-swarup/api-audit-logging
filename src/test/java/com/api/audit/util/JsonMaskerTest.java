package com.api.audit.util;

import static org.junit.jupiter.api.Assertions.*;

import com.api.audit.config.AuditLoggingProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonMaskerTest {

  private JsonMasker maskerWithDefaults() {
    return new JsonMasker(new AuditLoggingProperties());
  }

  private JsonMasker maskerWithAdditional(String... fields) {
    AuditLoggingProperties props = new AuditLoggingProperties();
    props.getMasking().setAdditionalFields(List.of(fields));
    return new JsonMasker(props);
  }

  @Test
  @DisplayName("GIVEN JSON with built-in sensitive keys WHEN mask called THEN values replaced")
  void testMask_BuiltInSensitiveData() {
    String input = "{\"password\":\"secret123\", \"token\": \"abc-123\", \"other\":\"data\"}";
    String result = maskerWithDefaults().mask(input);

    assertTrue(result.contains("\"password\":\"******\""));
    assertTrue(result.contains("\"token\":\"******\""));
    assertTrue(result.contains("\"other\":\"data\""));
  }

  @Test
  @DisplayName("GIVEN null input WHEN mask called THEN return null")
  void testMask_Null() {
    assertNull(maskerWithDefaults().mask(null));
  }

  @Test
  @DisplayName("GIVEN case-insensitive keys WHEN mask called THEN correctly identified")
  void testMask_CaseInsensitive() {
    String input = "{\"PASSWORD\":\"secret\"}";
    String result = maskerWithDefaults().mask(input);
    assertTrue(result.contains("\"PASSWORD\":\"******\""));
  }

  @Test
  @DisplayName("GIVEN additional consumer-configured fields WHEN mask called THEN also masked")
  void testMask_AdditionalConfiguredFields() {
    String input = "{\"otp\":\"123456\", \"nationalId\":\"AB123\", \"name\":\"Puneet\"}";
    String result = maskerWithAdditional("otp", "nationalId").mask(input);

    assertTrue(result.contains("\"otp\":\"******\""));
    assertTrue(result.contains("\"nationalId\":\"******\""));
    assertTrue(result.contains("\"name\":\"Puneet\""));
  }

  @Test
  @DisplayName(
      "GIVEN duplicate fields across built-in and additional WHEN mask called THEN no errors")
  void testMask_DuplicateFields_NoError() {
    // 'password' is in both built-in and additional — should not cause issues
    String input = "{\"password\":\"secret\"}";
    String result = maskerWithAdditional("password").mask(input);
    assertTrue(result.contains("\"password\":\"******\""));
  }
}
