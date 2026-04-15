package com.api.audit.util;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonMaskerTest {

  @Test
  @DisplayName(
      "GIVEN JSON with sensitive keys WHEN mask called THEN values are replaced with asterisks")
  void testMask_SensitiveData() {
    String input = "{\"password\":\"secret123\", \"token\": \"abc-123\", \"other\":\"data\"}";
    String result = JsonMasker.mask(input);

    assertTrue(result.contains("\"password\":\"******\""));
    assertTrue(result.contains("\"token\":\"******\""));
    assertTrue(result.contains("\"other\":\"data\""));
  }

  @Test
  @DisplayName("GIVEN null input WHEN mask called THEN return null (Branch Coverage)")
  void testMask_Null() {
    assertNull(JsonMasker.mask(null));
  }

  @Test
  @DisplayName("GIVEN case insensitive keys WHEN mask called THEN keys are correctly identified")
  void testMask_CaseInsensitive() {
    String input = "{\"PASSWORD\":\"secret\"}";
    String result = JsonMasker.mask(input);
    assertTrue(result.contains("\"PASSWORD\":\"******\""));
  }

  @Test
  @DisplayName("Private constructor coverage")
  void testConstructor() throws Exception {
    Constructor<JsonMasker> constructor = JsonMasker.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    assertNotNull(constructor.newInstance());
  }
}
