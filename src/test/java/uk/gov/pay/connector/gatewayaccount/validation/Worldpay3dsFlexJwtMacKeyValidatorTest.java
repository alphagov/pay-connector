package uk.gov.pay.connector.gatewayaccount.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Worldpay3dsFlexJwtMacKeyValidatorTest {
    
    private Worldpay3dsFlexJwtMacKeyValidator validator = new Worldpay3dsFlexJwtMacKeyValidator();
    
    @Test
    void test_for_valid_value() {
        assertTrue(validator.isValid(UUID.randomUUID().toString(), null));
    }
    
    @Test
    void test_for_invalid_uppercase_UUID() {
        assertFalse(validator.isValid(UUID.randomUUID().toString().toUpperCase(), null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "53f0917f101a442", "incorrect", "abcdef", "12345678901234567890123_",
            "h19da1dd-35ee-4ffc-ba41-383e356f020c", "d19da1dd-35ee-4ffc-ba41-383e356f02"})
    void test_for_invalid_values(String value) {
        assertFalse(validator.isValid(value, null));
    }
}
