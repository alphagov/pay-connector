package uk.gov.pay.connector.gateway.sandbox;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;

import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.SANDBOX;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;
    private GatewayAccountEntity gatewayAccountEntity;
    private GatewayAccountCredentialsEntity credentialsEntity;

    private static final String AUTH_SUCCESS_CARD_NUMBER = "4242424242424242";
    private static final String AUTH_REJECTED_CARD_NUMBER = "4000000000000069";
    private static final String AUTH_ERROR_CARD_NUMBER = "4000000000000119";

    @BeforeEach
    void setup() {
        provider = new SandboxPaymentProvider();
        credentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of())
                .withPaymentProvider(SANDBOX.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity = new GatewayAccountEntity();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(credentialsEntity));
    }

    @Test
    void getPaymentGatewayName_shouldGetExpectedName() {
        assertThat(provider.getPaymentGatewayName().getName(), is("sandbox"));
    }

    @Test
    void shouldGenerateTransactionId() {
        assertThat(provider.generateTransactionId().isPresent(), is(true));
        assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    void authorise_shouldBeAuthorisedWhenCardNumIsExpectedToSucceedForAuthorisation() {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_SUCCESS_CARD_NUMBER);
        GatewayResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.authoriseStatus(), is(AuthoriseStatus.AUTHORISED));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    void authorise_shouldNotBeAuthorisedWhenCardNumIsExpectedToBeRejectedForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_REJECTED_CARD_NUMBER);
        GatewayResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.authoriseStatus(), is(AuthoriseStatus.REJECTED));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    void authorise_shouldGetGatewayErrorWhenCardNumIsExpectedToFailForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_ERROR_CARD_NUMBER);
        GatewayResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("This transaction could be not be processed."));
    }

    @Test
    void authorise_shouldGetGatewayErrorWhenCardNumDoesNotExistForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("3456789987654567");
        GatewayResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("Unsupported card details."));
    }

    @Test
    void refund_shouldSucceedWhenRefundingAnyCharge() {
        ChargeEntity chargeEntity = ChargeEntityFixture
                .aValidChargeEntity()
                .withPaymentProvider(SANDBOX.getName())
                .build();
        GatewayRefundResponse refundResponse = provider.refund(RefundGatewayRequest.valueOf(Charge.from(chargeEntity), RefundEntityFixture.aValidRefundEntity().build(), gatewayAccountEntity, credentialsEntity));

        assertThat(refundResponse.isSuccessful(), is(true));
        assertThat(refundResponse.getReference().isPresent(), is(true));
        assertThat(refundResponse.getReference(), is(notNullValue()));

        assertThat(refundResponse.getError().isPresent(), is(false));
    }

    @Test
    void cancel_shouldSucceedWhenCancellingAnyCharge() {

        GatewayResponse<BaseCancelResponse> gatewayResponse = provider.cancel(CancelGatewayRequest.valueOf(ChargeEntityFixture.aValidChargeEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));

        BaseCancelResponse cancelResponse = gatewayResponse.getBaseResponse().get();
        assertThat(cancelResponse.getTransactionId(), is(notNullValue()));
        assertThat(cancelResponse.getErrorCode(), is(nullValue()));
        assertThat(cancelResponse.getErrorMessage(), is(nullValue()));
    }
}
