package uk.gov.pay.connector.gateway.adyen.response.json;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

class Authorise3dsResponseBodyTest {
    
    @Test
    void should_format_toString_with_both_fields_present() {
        var response = new Authorise3dsResponseBody("psp-ref-123", "Authorised",
                null, null, null);
        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123, resultCode: Authorised)"));
    }

    @Test
    void should_exclude_null_pspReference_from_toString() {
        var response = new Authorise3dsResponseBody(null, "Authorised", 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }

    @Test
    void should_exclude_null_resultCode_from_toString() {
        var response = new Authorise3dsResponseBody("psp-ref-123", null, 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123)"));
    }

    @Test
    void should_exclude_blank_pspReference_from_toString() {
        var response = new Authorise3dsResponseBody("", "Authorised", 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }

    @Test
    void should_exclude_blank_resultCode_from_toString() {
        var response = new Authorise3dsResponseBody("psp-ref-123", "", 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123)"));
    }

    @Test
    void should_return_empty_response_when_both_fields_null() {
        var response = new Authorise3dsResponseBody(null, null, 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response ()"));
    }

    @Test
    void should_return_empty_response_when_both_fields_blank() {
        var response = new Authorise3dsResponseBody("", "", 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response ()"));
    }

    @Test
    void should_exclude_whitespace_only_fields_from_toString() {
        var response = new Authorise3dsResponseBody("   ", "Authorised", 
                null, null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }

    @Test
    void getStoredPaymentMethodId_ShouldReturnNull_WhenAdditionalDataIsNull() {
        Authorise3dsResponseBody response = new Authorise3dsResponseBody(
                "psp-ref-123",
                "Authorised",
                null, null, null
        );

        String result = response.getStoredPaymentMethodId();

        assertNull(result);
    }

    @Test
    void getStoredPaymentMethodId_ShouldReturnId_WhenAdditionalDataIsPresent() {
        String expectedId = "someStoredPaymentMethodId";

        var additionalData = new AdditionalData(expectedId);

        Authorise3dsResponseBody response = new Authorise3dsResponseBody(
                "psp-ref-123",
                "Authorised",
                additionalData, null, null
        );

        String result = response.getStoredPaymentMethodId();

        assertEquals(expectedId, result);
    }
}


