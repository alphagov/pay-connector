package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;

@RunWith(MockitoJUnitRunner.class)
public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;

    private static final String AUTH_SUCCESS_CARD_NUMBER = "4242424242424242";
    private static final String AUTH_REJECTED_CARD_NUMBER = "4000000000000069";
    private static final String AUTH_ERROR_CARD_NUMBER = "4000000000000119";

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        provider = new SandboxPaymentProvider();
    }

    @Test
    public void getPaymentGatewayName_shouldGetExpectedName() {
        Assert.assertThat(provider.getPaymentGatewayName().getName(), is("sandbox"));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(true));
        Assert.assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void authorise_shouldBeAuthorisedWhenCardNumIsExpectedToSucceedForAuthorisation() {
        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_SUCCESS_CARD_NUMBER);
        PaymentProviderAuthorisationResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));

        assertTrue(gatewayResponse.getAuthoriseStatus().isPresent());
        assertThat(gatewayResponse.getAuthoriseStatus().get(), is(AuthoriseStatus.AUTHORISED));
        assertThat(gatewayResponse.getTransactionId(), is(notNullValue()));
    }

    @Test
    public void authorise_shouldNotBeAuthorisedWhenCardNumIsExpectedToBeRejectedForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_REJECTED_CARD_NUMBER);
        PaymentProviderAuthorisationResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertFalse(gatewayResponse.getGatewayError().isPresent());
        assertTrue(gatewayResponse.getTransactionId().isPresent());
        assertTrue(gatewayResponse.getAuthoriseStatus().isPresent());
        assertThat(gatewayResponse.getAuthoriseStatus().get(), is(AuthoriseStatus.REJECTED));
    }

    @Test
    public void authorise_shouldGetGatewayErrorWhenCardNumIsExpectedToFailForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo(AUTH_ERROR_CARD_NUMBER);
        PaymentProviderAuthorisationResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertFalse(gatewayResponse.getAuthoriseStatus().isPresent());

        assertTrue(gatewayResponse.getGatewayError().isPresent());
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is("This transaction could be not be processed."));
    }

    @Test
    public void authorise_shouldGetGatewayErrorWhenCardNumDoesNotExistForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("3456789987654567");
        PaymentProviderAuthorisationResponse gatewayResponse = provider.authorise(new CardAuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertFalse(gatewayResponse.getAuthoriseStatus().isPresent());
        assertTrue(gatewayResponse.getGatewayError().isPresent());

        assertTrue(gatewayResponse.getGatewayError().isPresent());
        assertThat(gatewayResponse.getGatewayError().get().getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayResponse.getGatewayError().get().getMessage(), is("Unsupported card details."));
    }

    @Test
    public void refund_shouldSucceedWhenRefundingAnyCharge() {

        GatewayResponse<BaseRefundResponse> gatewayResponse = provider.refund(RefundGatewayRequest.valueOf(RefundEntityFixture.aValidRefundEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));

        BaseRefundResponse refundResponse = gatewayResponse.getBaseResponse().get();
        assertThat(refundResponse.getReference(), is(notNullValue()));
        assertThat(refundResponse.getErrorCode(), is(nullValue()));
        assertThat(refundResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void cancel_shouldSucceedWhenCancellingAnyCharge() {

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
