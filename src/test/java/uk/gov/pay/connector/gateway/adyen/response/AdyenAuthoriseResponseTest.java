package uk.gov.pay.connector.gateway.adyen.response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.gateway.adyen.response.json.AuthoriseResponseBody;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;

import java.util.Map;

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
        var response = new AuthoriseResponseBody(
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
        var response = new AuthoriseResponseBody(
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

    @Test
    void should_set_httpMethod3ds_null_if_action_is_null() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(null)
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getHttpMethod3ds(), is(nullValue()));
    }

    @Test
    void should_set_httpMethod3ds_null_if_action_method_is_null() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withMethod(null).build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getHttpMethod3ds(), is(nullValue()));
    }

    @Test
    void should_set_httpMethod3ds_to_action_method() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withMethod("GET").build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getHttpMethod3ds(), is("GET"));
    }

    @Test
    void should_set_data_to_action_data() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withData(Map.of("MD", "testMD123", "PaReq", "testPaReq")).build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getMd(), is("testMD123"));
        assertThat(adyenAuthoriseResponse.getPaReq(), is("testPaReq"));
    }

    @Test
    void should_not_set_data_to_action_data_if_Adyen_does_not_return_it() {
        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withAction(anAction().withMethod("GET").build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        assertThat(adyenAuthoriseResponse.getHttpMethod3ds(), is("GET"));

        assertThat(adyenAuthoriseResponse.getPaReq(), nullValue());
        assertThat(adyenAuthoriseResponse.getMd(), nullValue());
    }

    @Test
    void should_extract_3ds_required_details_for_redirect_shopper_response() {
        var redirectUrl = "https://checkoutshopper-test.adyen.com/checkoutshopper/threeDS/redirect";
        var httpMethod = "GET";

        var adyenPaymentResponse = anAdyenPaymentResponse()
                .withResultCode("RedirectShopper")
                .withAction(anAction()
                        .withUrl(redirectUrl)
                        .withMethod(httpMethod)
                        .withData(Map.of("MD", "testMD123", "PaReq", "testPaReq123"))
                        .build())
                .build();

        var adyenAuthoriseResponse = AdyenAuthoriseResponse.of(adyenPaymentResponse);

        var auth3dsRequiredDetails = adyenAuthoriseResponse.extractAuth3dsRequiredDetails();

        assertThat(auth3dsRequiredDetails.isPresent(), is(true));
        assertThat(auth3dsRequiredDetails.get().getIssuerUrl(), is(redirectUrl));
        assertThat(auth3dsRequiredDetails.get().getHttpMethod3ds(), is(httpMethod));
        assertThat(auth3dsRequiredDetails.get().getPaRequest(), is("testPaReq123"));
        assertThat(auth3dsRequiredDetails.get().getMd(), is("testMD123"));
    }
}
