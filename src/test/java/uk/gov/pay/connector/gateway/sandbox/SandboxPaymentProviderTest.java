package uk.gov.pay.connector.gateway.sandbox;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse.AuthoriseStatus;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.ExternalRefundAvailabilityCalculator;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.model.domain.RefundEntityFixture;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability.EXTERNAL_AVAILABLE;

@RunWith(MockitoJUnitRunner.class)
public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;

    @Mock
    private ExternalRefundAvailabilityCalculator mockExternalRefundAvailabilityCalculator;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setup() {
        provider = new SandboxPaymentProvider(mockExternalRefundAvailabilityCalculator);
    }

    @Test
    public void getPaymentGatewayName_shouldGetExpectedName() {
        Assert.assertThat(provider.getPaymentGatewayName().getName(), is("sandbox"));
    }

    @Test
    public void GetStatusMapper_shouldGetExpectedInstance() {
        Assert.assertThat(provider.getStatusMapper(), sameInstance(SandboxStatusMapper.get()));
    }

    @Test
    public void shouldGenerateTransactionId() {
        Assert.assertThat(provider.generateTransactionId().isPresent(), is(true));
        Assert.assertThat(provider.generateTransactionId().get(), is(instanceOf(String.class)));
    }

    @Test
    public void shouldAlwaysVerifyNotification() {
        Assert.assertThat(provider.verifyNotification(null, mock(GatewayAccountEntity.class)), is(true));
    }

    @Test
    public void parseNotification_shouldFailParsingNotification() throws Exception {

        String notification = "{\"transaction_id\":\"1\",\"status\":\"BOOM\", \"reference\":\"abc\"}";

        expectedException.expect(UnsupportedOperationException.class);
        expectedException.expectMessage(is("Sandbox account does not support notifications"));

        provider.parseNotification(notification);
    }

    @Test
    public void authorise_shouldBeAuthorisedWhenCardNumIsExpectedToSucceedForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("4242424242424242");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

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
    public void authorise_shouldNotBeAuthorisedWhenCardNumIsExpectedToBeRejectedForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("4000000000000069");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

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
    public void authorise_shouldGetGatewayErrorWhenCardNumIsExpectedToFailForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("4000000000000119");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("This transaction could be not be processed."));
    }

    @Test
    public void authorise_shouldGetGatewayErrorWhenCardNumDoesNotExistForAuthorisation() {

        AuthCardDetails authCardDetails = new AuthCardDetails();
        authCardDetails.setCardNo("3456789987654567");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), authCardDetails));

        assertThat(gatewayResponse.isSuccessful(), is(false));
        assertThat(gatewayResponse.isFailed(), is(true));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(false));

        GatewayError gatewayError = (GatewayError) gatewayResponse.getGatewayError().get();
        assertThat(gatewayError.getErrorType(), is(GENERIC_GATEWAY_ERROR));
        assertThat(gatewayError.getMessage(), is("Unsupported card details."));
    }

    @Test
    public void capture_shouldSucceedWhenCapturingAnyCharge() {

        GatewayResponse<BaseCaptureResponse> gatewayResponse = provider.capture(CaptureGatewayRequest.valueOf(ChargeEntityFixture.aValidChargeEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));

        BaseCaptureResponse captureResponse = gatewayResponse.getBaseResponse().get();
        assertThat(captureResponse.getTransactionId(), is(notNullValue()));
        assertThat(captureResponse.getErrorCode(), is(nullValue()));
        assertThat(captureResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void capture_shouldSucceedWhenCancellingAnyCharge() {

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
    public void shouldReturnExternalRefundAvailability() {
        ChargeEntity mockChargeEntity = mock(ChargeEntity.class);
        when(mockExternalRefundAvailabilityCalculator.calculate(mockChargeEntity)).thenReturn(EXTERNAL_AVAILABLE);
        assertThat(provider.getExternalChargeRefundAvailability(mockChargeEntity), is(EXTERNAL_AVAILABLE));
    }

}
