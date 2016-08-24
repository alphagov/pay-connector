package uk.gov.pay.connector.service.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fj.data.Either;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import uk.gov.pay.connector.model.*;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.model.gateway.AuthorisationGatewayRequest;
import uk.gov.pay.connector.model.gateway.GatewayResponse;
import uk.gov.pay.connector.service.BaseAuthoriseResponse;
import uk.gov.pay.connector.service.BaseCancelResponse;
import uk.gov.pay.connector.service.BaseCaptureResponse;
import uk.gov.pay.connector.service.sandbox.SandboxPaymentProvider;
import uk.gov.pay.connector.service.sandbox.SandboxStatusMapper;
import uk.gov.pay.connector.service.worldpay.WorldpayStatusMapper;

import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsSame.sameInstance;
import static org.mockito.Mockito.mock;
import static uk.gov.pay.connector.model.ErrorType.GENERIC_GATEWAY_ERROR;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class SandboxPaymentProviderTest {

    private SandboxPaymentProvider provider;

    @Rule
    public ExpectedException expectedException =  ExpectedException.none();

    @Before
    public void setup() {
        provider = new SandboxPaymentProvider(new ObjectMapper());
    }

    @Test
    public void getPaymentGatewayName_shouldGetExpectedName() {
        Assert.assertThat(provider.getPaymentGatewayName(), is("sandbox"));
    }

    @Test
    public void GetStatusMapper_shouldGetExpectedInstance() {
        Assert.assertThat(provider.getStatusMapper(), sameInstance(SandboxStatusMapper.get()));
    }

    @Test
    public void generateTransactionId_shouldGenerateANonNullValue() {
        assertThat(provider.generateTransactionId(), is(notNullValue()));
    }

    @Test
    public void parseNotification_shouldSuccessfullyParse() throws Exception {

        String notification = "{\"transaction_id\":\"1\",\"status\":\"BOOM\"}";

        Either<String, Notifications<String>> parsedNotification = provider.parseNotification(notification);

        assertThat(parsedNotification.isRight(), is(true));

        ImmutableList<Notification<String>> notifications = parsedNotification.right().value().get();

        assertThat(notifications.size(), is(1));
        Notification<String> sandboxNotification = notifications.get(0);

        assertThat(sandboxNotification.getStatus(), is("BOOM"));
        assertThat(sandboxNotification.getTransactionId(), is("1"));
    }

    @Test
    public void parseNotification_shouldFailWhenNotificationIsInvalid() {

        String notification = "What is this!!!";

        Either<String, Notifications<String>> parsedNotification = provider.parseNotification(notification);

        assertThat(parsedNotification.isLeft(), is(true));
        assertThat(parsedNotification.left().value(), is("Error understanding sandbox notification: What is this!!!"));
    }

    @Test
    public void authorise_shouldBeAuthorisedWhenCardNumIsExpectedToSucceedForAuthorisation() {

        Card card = new Card();
        card.setCardNo("4242424242424242");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), card));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.isAuthorised(), is(true));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void authorise_shouldNotBeAuthorisedWhenCardNumIsExpectedToBeRejectedForAuthorisation() {

        Card card = new Card();
        card.setCardNo("4000000000000069");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), card));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseAuthoriseResponse, is(true));

        BaseAuthoriseResponse authoriseResponse = (BaseAuthoriseResponse) gatewayResponse.getBaseResponse().get();
        assertThat(authoriseResponse.isAuthorised(), is(false));
        assertThat(authoriseResponse.getTransactionId(), is(notNullValue()));
        assertThat(authoriseResponse.getErrorCode(), is(nullValue()));
        assertThat(authoriseResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void authorise_shouldGetGatewayErrorWhenCardNumIsExpectedToFailForAuthorisation() {

        Card card = new Card();
        card.setCardNo("4000000000000119");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), card));

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

        Card card = new Card();
        card.setCardNo("3456789987654567");
        GatewayResponse gatewayResponse = provider.authorise(new AuthorisationGatewayRequest(ChargeEntityFixture.aValidChargeEntity().build(), card));

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

        GatewayResponse gatewayResponse = provider.capture(CaptureGatewayRequest.valueOf(ChargeEntityFixture.aValidChargeEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseCaptureResponse, is(true));

        BaseCaptureResponse captureResponse = (BaseCaptureResponse) gatewayResponse.getBaseResponse().get();
        assertThat(captureResponse.getTransactionId(), is(notNullValue()));
        assertThat(captureResponse.getErrorCode(), is(nullValue()));
        assertThat(captureResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void capture_shouldSucceedWhenCancellingAnyCharge() {

        GatewayResponse gatewayResponse = provider.cancel(CancelGatewayRequest.valueOf(ChargeEntityFixture.aValidChargeEntity().build()));

        assertThat(gatewayResponse.isSuccessful(), is(true));
        assertThat(gatewayResponse.isFailed(), is(false));
        assertThat(gatewayResponse.getGatewayError().isPresent(), is(false));
        assertThat(gatewayResponse.getBaseResponse().isPresent(), is(true));
        assertThat(gatewayResponse.getBaseResponse().get() instanceof BaseCancelResponse, is(true));

        BaseCancelResponse cancelResponse = (BaseCancelResponse) gatewayResponse.getBaseResponse().get();
        assertThat(cancelResponse.getTransactionId(), is(notNullValue()));
        assertThat(cancelResponse.getErrorCode(), is(nullValue()));
        assertThat(cancelResponse.getErrorMessage(), is(nullValue()));
    }

    @Test
    public void refund_shouldFailWhenRefundingAnyCharge() {
        expectedException.expect(UnsupportedOperationException.class);
        provider.refund(RefundGatewayRequest.valueOf(new RefundEntity(ChargeEntityFixture.aValidChargeEntity().build(), 1L)));
    }
}
