package uk.gov.pay.connector.gateway.sandbox.wallets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.sandbox.SandboxGatewayResponseGenerator;
import uk.gov.pay.connector.gateway.sandbox.SandboxLast4DigitsCardNumbers;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.service.payments.commons.model.CardExpiryDate;

import java.time.YearMonth;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture.anApplePayAuthRequest;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

class SandboxWalletAuthorisationHandlerTest {

    private SandboxWalletAuthorisationHandler sandboxWalletAuthorisationHandler;

    @BeforeEach
    public void setup() {
        sandboxWalletAuthorisationHandler = new SandboxWalletAuthorisationHandler(new SandboxGatewayResponseGenerator(new SandboxLast4DigitsCardNumbers()));
    }

    @Test
    void authorise_shouldBeAuthorisedWhenChargeDescriptionIsNotMagicValue_forApplePay() {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber("1234")
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authoriseApplePay(
                new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().withDescription("whatever").build(), applePayAuthRequest));

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
        assertThat(authoriseResponse.getCardExpiryDate().isPresent(), is(true));
        assertThat(authoriseResponse.getCardExpiryDate().get(), is(CardExpiryDate.valueOf(YearMonth.of(2050, 12))));
    }

    @ParameterizedTest
    @EnumSource(value = SandboxWalletMagicValues.class, names = {"DECLINED", "REFUSED"})
    void authorise_shouldNotBeAuthorisedWithAnyLastDigitsCardNumbersWhenMagicValuesArePresent_forApplePay(SandboxWalletMagicValues magicValue) {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber("5678")
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authoriseApplePay(
                new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().withDescription(magicValue.name()).build(), applePayAuthRequest));

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
        assertThat(authoriseResponse.getCardExpiryDate().isPresent(), is(true));
        assertThat(authoriseResponse.getCardExpiryDate().get(), is(CardExpiryDate.valueOf(YearMonth.of(2050, 12))));
    }

    @Test
    void authorise_shouldGetGatewayErrorWithAnyLastDigitsCardNumbersWhenMagicValueIsPresent_forApplePay() {
        ApplePayAuthRequest applePayAuthRequest =
                anApplePayAuthRequest()
                        .withApplePaymentInfo(
                                anApplePayPaymentInfo()
                                        .withLastDigitsCardNumber("9999")
                                        .build())
                        .build();
        GatewayResponse gatewayResponse = sandboxWalletAuthorisationHandler.authoriseApplePay(
                new ApplePayAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().withDescription(SandboxWalletMagicValues.ERROR.name()).build(), applePayAuthRequest));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("This transaction could be not be processed."));
    }
}
