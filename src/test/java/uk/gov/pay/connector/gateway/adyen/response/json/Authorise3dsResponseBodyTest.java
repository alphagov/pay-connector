package uk.gov.pay.connector.gateway.adyen.response.json;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class Authorise3dsResponseBodyTest {

    @Test
    void should_format_toString_with_both_fields_present() {
        var response = new Authorise3dsResponseBody("psp-ref-123", "Authorised");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123, resultCode: Authorised)"));
    }

    @Test
    void should_exclude_null_pspReference_from_toString() {
        var response = new Authorise3dsResponseBody(null, "Authorised");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }

    @Test
    void should_exclude_null_resultCode_from_toString() {
        var response = new Authorise3dsResponseBody("psp-ref-123", null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123)"));
    }

    @Test
    void should_exclude_blank_pspReference_from_toString() {
        var response = new Authorise3dsResponseBody("", "Authorised");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }

    @Test
    void should_exclude_blank_resultCode_from_toString() {
        var response = new Authorise3dsResponseBody("psp-ref-123", "");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (pspReference: psp-ref-123)"));
    }

    @Test
    void should_return_empty_response_when_both_fields_null() {
        var response = new Authorise3dsResponseBody(null, null);

        assertThat(response.toString(), is("Adyen Authorise 3DS Response ()"));
    }

    @Test
    void should_return_empty_response_when_both_fields_blank() {
        var response = new Authorise3dsResponseBody("", "");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response ()"));
    }

    @Test
    void should_exclude_whitespace_only_fields_from_toString() {
        var response = new Authorise3dsResponseBody("   ", "Authorised");

        assertThat(response.toString(), is("Adyen Authorise 3DS Response (resultCode: Authorised)"));
    }
}


