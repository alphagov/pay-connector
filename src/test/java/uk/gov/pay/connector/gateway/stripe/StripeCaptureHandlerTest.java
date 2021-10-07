package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.request.StripeCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferOutRequest;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.eclipse.jetty.http.HttpStatus.INTERNAL_SERVER_ERROR_500;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@RunWith(MockitoJUnitRunner.class)
public class StripeCaptureHandlerTest {

    private StripeCaptureHandler stripeCaptureHandler;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient gatewayClient;

    private CaptureGatewayRequest captureGatewayRequest;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayAccountEntity gatewayAccount;
    private String transactionId = "ch_1231231123123";

    @Before
    public void setup() {
        stripeCaptureHandler = new StripeCaptureHandler(gatewayClient, stripeGatewayConfig, objectMapper);
        when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.08);
        when(stripeGatewayConfig.getFeePercentageV2Date()).thenReturn(Instant.now().plusSeconds(1));
        when(stripeGatewayConfig.isCollectFee()).thenReturn(true);

        gatewayAccount = buildGatewayAccountEntity();

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
    }

    @Test
    public void shouldCaptureWithFeeAndTransferCorrectAmountToConnectAccount() throws Exception {
        mockStripeCaptureAndTransfer();

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient, times(2)).postRequestFor(transferRequestCaptor.capture());

        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9942"));

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(captureResponse.getTransactionId().isPresent(), is(true));
        assertThat(captureResponse.getTransactionId().get(), is(captureGatewayRequest.getTransactionId()));
        assertThat(captureResponse.getFee().isPresent(), is(true));
        assertThat(captureResponse.getFee().get(), is(58L));
    }

    @Test
    public void shouldCaptureWithNewFeePercentageForChargesCreatedAfterConfiguredFeePercentageV2Date() throws Exception {
        mockStripeCaptureAndTransfer();

        int chargeCreatedDate = 1629936000; //26Aug2021
        int feeV2DateBeforeChargeCreatedDate = 1629849600; //25Aug2021  ;

        when(stripeGatewayConfig.getFeePercentageV2Date()).thenReturn(Instant.ofEpochSecond(feeV2DateBeforeChargeCreatedDate));
        when(stripeGatewayConfig.getFeePercentageV2()).thenReturn(0.20);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedDate))
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .build())
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient, times(2)).postRequestFor(transferRequestCaptor.capture());

        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9930"));

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(70L));
    }

    @Test
    public void shouldCaptureWithV1FeeAndTransferCorrectAmountToConnectAccount() throws Exception {
        mockStripeCaptureAndTransfer();

        int chargeCreatedDate = 1629849600; //25Aug2021
        int feeV2DateAfterChargeCreatedDate = 1629936000; //26Aug2021  ;

        when(stripeGatewayConfig.getFeePercentageV2Date()).thenReturn(Instant.ofEpochSecond(feeV2DateAfterChargeCreatedDate));
        when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.50);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedDate))
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .build())
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient, times(2)).postRequestFor(transferRequestCaptor.capture());

        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9900"));

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(100L));
    }

    @Test
    public void shouldCaptureWithFee_feeCalculationShouldAlwaysRoundUp() throws Exception {
        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10001L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();

        CaptureGatewayRequest captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        mockStripeCaptureAndTransfer();

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(59L));
    }

    @Test
    public void shouldCaptureWithFee_feeCalculationShouldRoundUpTo1() throws Exception {
        final String transactionId = "ch_1231231123123";
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(1L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();

        CaptureGatewayRequest captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);
        mockStripeCaptureAndTransfer();

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.getFee().get(), is(51L));
    }

    @Test
    public void shouldCaptureWithoutFee_ifCollectFeeSetToFalse() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenReturn(gatewayTransferResponse);

        when(stripeGatewayConfig.isCollectFee()).thenReturn(false);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertTrue(response.isSuccessful());
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().isPresent(), is(true));
        assertThat(response.getTransactionId().get(), is(transactionId));
        assertThat(response.getFee().isPresent(), is(false));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns4xxHttpStatusCode() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), SC_UNAUTHORIZED);
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenThrow(exception);
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("error: No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns5xxHttpStatusCode() throws Exception {
        GatewayErrorException exception = new GatewayErrorException("uh oh", "Problem with Stripe servers", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenThrow(exception);
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred when capturing charge_external_id: " + captureGatewayRequest.getExternalId()));
        assertThat(response.getError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns4XXOnTransfer() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", load(STRIPE_ERROR_RESPONSE), SC_UNAUTHORIZED);
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenThrow(exception);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    public void shouldCorrectlyCaptureUsingPaymentIntentsApi() throws Exception {
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("pi_123")
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .build();

        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripePaymentIntentCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenReturn(gatewayTransferResponse);

        CaptureResponse response = stripeCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().get(), is("pi_123"));
    }

    @Test
    public void shouldNotCaptureIfPaymentProviderReturns5XXOnTransfer() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        GatewayErrorException exception = new GatewayErrorException("uh oh", "Problem with Stripe servers", INTERNAL_SERVER_ERROR_500);
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenThrow(exception);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.getError().get().getMessage(), containsString("An internal server error occurred when capturing charge_external_id: " + captureGatewayRequest.getExternalId()));
        assertThat(response.getError().get().getErrorType(), is(GATEWAY_ERROR));
    }

    private GatewayAccountEntity buildGatewayAccountEntity() {
        var gatewayAccountEntity = aGatewayAccountEntity()
                .withId(1L)
                .withGatewayName("stripe")
                .withRequires3ds(false)
                .withType(TEST)
                .build();
        var gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                .withGatewayAccountEntity(gatewayAccountEntity)
                .withPaymentProvider(STRIPE.getName())
                .withState(ACTIVE)
                .build();
        gatewayAccountEntity.setGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity));

        return gatewayAccountEntity;
    }

    private void mockStripeCaptureAndTransfer() throws GatewayException.GenericGatewayException, GatewayErrorException,
            GatewayException.GatewayConnectionTimeoutException {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));

        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenReturn(gatewayTransferResponse);
    }

}
