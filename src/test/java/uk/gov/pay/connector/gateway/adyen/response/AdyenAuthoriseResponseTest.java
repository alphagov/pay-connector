package uk.gov.pay.connector.gateway.adyen.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenPaymentResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.adyen.response.ActionFixture.anAction;
import static uk.gov.pay.connector.gateway.adyen.response.AdyenPaymentResponseFixture.anAdyenPaymentResponse;

class AdyenAuthoriseResponseTest {

    @ParameterizedTest
    @CsvSource({
            "Authorised, AUTHORISED",
            "Refused, REJECTED",
            "RedirectShopper, REQUIRES_3DS",
            "Error, ERROR"
    })
    void should_map_Adyen_result_codes_to_Pay_AuthoriseStatuses(String adyenResultCode, BaseAuthoriseResponse.AuthoriseStatus expectedStatus) {
        var response = new AdyenPaymentResponse(
                "psp-reference",
                adyenResultCode,
                "refusal-reason",
                "0",
                null);

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(response);

        assertThat(adyenAuthoriseResponse.authoriseStatus(), is(expectedStatus));
    }

    @Test
    void should_throw_IllegalStateException_on_an_unrecognised_Adyen_result_code() {
        var response = new AdyenPaymentResponse(
                "psp-reference",
                "UNRECOGNISED_RESULT_CODE",
                "refusal-reason",
                "0",
                null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> AdyenAuthoriseResponse.of(response));
        assertThat(exception.getMessage(), is("Unexpected value: UNRECOGNISED_RESULT_CODE"));
    }

    @Test
    void should_set_redirectUrl_null_if_action_is_null() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(null)
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getRedirectUrl(), is(nullValue()));
    }

    @Test
    void should_set_redirectUrl_null_if_action_URL_is_null() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withUrl(null).build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getRedirectUrl(), is(nullValue()));
    }

    @Test
    void should_set_redirectUrl_to_action_URL() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withUrl("/redirectUrl").build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getRedirectUrl(), is("/redirectUrl"));
    }
}
