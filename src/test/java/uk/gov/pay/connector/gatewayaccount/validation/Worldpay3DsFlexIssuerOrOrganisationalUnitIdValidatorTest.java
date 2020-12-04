package uk.gov.pay.connector.gatewayaccount.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Worldpay3DsFlexIssuerOrOrganisationalUnitIdValidatorTest {
    
    private Worldpay3dsFlexIssuerOrOrganisationalUnitIdValidator validator = new Worldpay3dsFlexIssuerOrOrganisationalUnitIdValidator();
    
    @Test
    void test_for_valid_value() {
        assertTrue(validator.isValid("53f0917f101a4428b69d5fb0", null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "53f0917f101a442", "incorrect", "abcdef", "12345678901234567890123_" })
    void test_for_invalid_value(String value) {
        assertFalse(validator.isValid(value, null));
    }
}
