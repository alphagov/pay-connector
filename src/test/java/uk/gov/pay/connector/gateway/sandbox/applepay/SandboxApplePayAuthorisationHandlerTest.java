package uk.gov.pay.connector.gateway.sandbox.applepay;

import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.util.AuthUtils;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;

public class SandboxApplePayAuthorisationHandlerTest {

    private SandboxApplePayAuthorisationHandler sandboxApplePayAuthorisationHandler;

    private static final String AUTH_SUCCESS_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "4242";
    private static final String AUTH_REJECTED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0002";
    private static final String AUTH_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0119";

    @Before
    public void setup() {
        sandboxApplePayAuthorisationHandler = new SandboxApplePayAuthorisationHandler();
    }

    @Test
    public void authorise_shouldBeAuthorisedWhenLastDigitsCardNumbersAreExpectedToSucceedForAuthorisation_forApplePay() {
        AppleDecryptedPaymentData applePaymentData = AuthUtils.ApplePay.buildDecryptedPaymentData("Mr. Payment", "mr@payment.test", AUTH_SUCCESS_APPLE_PAY_LAST_DIGITS_CARD_NUMBER);

        GatewayResponse gatewayResponse = sandboxApplePayAuthorisationHandler.authorise(new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePaymentData));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void authorise_shouldNotBeAuthorisedWhenLastDigitsCardNumbersAreExpectedToBeRejectedForAuthorisation_forApplePay() {
        AppleDecryptedPaymentData applePaymentData = AuthUtils.ApplePay.buildDecryptedPaymentData("Mr. Payment", "mr@payment.test", AUTH_REJECTED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER);
        GatewayResponse gatewayResponse = sandboxApplePayAuthorisationHandler.authorise(new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePaymentData));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.authoriseStatus(), is(BaseAuthoriseResponse.AuthoriseStatus.REJECTED));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void authorise_shouldGetGatewayErrorWhenLastDigitsCardNumbersAreExpectedToFailForAuthorisation_forApplePay() {
        AppleDecryptedPaymentData applePaymentData = AuthUtils.ApplePay.buildDecryptedPaymentData("Mr. Payment", "mr@payment.test", AUTH_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER);
        GatewayResponse gatewayResponse = sandboxApplePayAuthorisationHandler.authorise(new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePaymentData));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("This transaction could be not be processed."));
    }
}
