package uk.gov.pay.connector.gateway.sandbox.applepay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.gateway.sandbox.SandboxLast4DigitsCardNumbers;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture.anApplePayAuthRequest;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

class SandboxWalletAuthorisationHandlerTest {

    private SandboxWalletAuthorisationHandler sandboxWalletAuthorisationHandler;

    private static final String AUTH_SUCCESS_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "4242";
    private static final String AUTH_REJECTED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0002";
    private static final String AUTH_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER = "0119";

    @BeforeEach
    public void setup() {
        sandboxWalletAuthorisationHandler = new SandboxWalletAuthorisationHandler(new SandboxGatewayResponseGenerator(new SandboxLast4DigitsCardNumbers()));
    }

    @Test
    void authorise_shouldBeAuthorisedWhenLastDigitsCardNumbersAreExpectedToSucceedForAuthorisation_forApplePay() {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber(AUTH_SUCCESS_APPLE_PAY_LAST_DIGITS_CARD_NUMBER)
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authorise(
                new WalletAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePayAuthRequest));

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
    void authorise_shouldNotBeAuthorisedWhenLastDigitsCardNumbersAreExpectedToBeRejectedForAuthorisation_forApplePay() {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber(AUTH_REJECTED_APPLE_PAY_LAST_DIGITS_CARD_NUMBER)
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authorise(
                new WalletAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePayAuthRequest));

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
    void authorise_shouldGetGatewayErrorWhenLastDigitsCardNumbersAreExpectedToFailForAuthorisation_forApplePay() {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber(AUTH_ERROR_APPLE_PAY_LAST_DIGITS_CARD_NUMBER)
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authorise(
                new WalletAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), applePayAuthRequest));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("This transaction could be not be processed."));
    }
}
