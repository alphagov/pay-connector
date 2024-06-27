package uk.gov.pay.connector.gateway.stripe;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.GatewayClientGetRequest;
import uk.gov.pay.connector.gateway.stripe.handler.StripeCaptureHandler;
import uk.gov.pay.connector.gateway.stripe.request.StripeCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeGetPaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeSearchTransfersRequest;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture.aValidChargeEntity;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;
import static uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity.ChargeEventEntityBuilder.aChargeEventEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;
import static uk.gov.pay.connector.pact.ChargeEventEntityFixture.aValidChargeEventEntity;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_ERROR_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_GET_PAYMENT_INTENT_WITH_MULTIPLE_CHARGES;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_PAYMENT_INTENT_WITHOUT_BALANCE_TRANSACTION_EXPANDED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_SEARCH_TRANSFERS_EMPTY_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_SEARCH_TRANSFERS_FOR_CAPTURED_PAYMENT_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.STRIPE_TRANSFER_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class StripeCaptureHandlerTest {

    private StripeCaptureHandler stripeCaptureHandler;

    @Mock
    private StripeGatewayConfig stripeGatewayConfig;
    @Mock
    private GatewayClient gatewayClient;

    @Captor
    ArgumentCaptor<GatewayClientGetRequest> gatewayClientGetRequestArgumentCaptor;
    @Captor
    ArgumentCaptor<StripeTransferOutRequest> stripeTransferOutRequestArgumentCaptor;

    private CaptureGatewayRequest captureGatewayRequest;
    private JsonObjectMapper objectMapper = new JsonObjectMapper(new ObjectMapper());
    private GatewayAccountEntity gatewayAccount;
    private String transactionId = "ch_1231231123123";

    @BeforeEach
    public void setup() {
        stripeCaptureHandler = new StripeCaptureHandler(gatewayClient, stripeGatewayConfig, objectMapper);
        lenient().when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.08);
        lenient().when(stripeGatewayConfig.getRadarFeeInPence()).thenReturn(5);
        lenient().when(stripeGatewayConfig.getThreeDsFeeInPence()).thenReturn(6);
        lenient().when(stripeGatewayConfig.isCollectFee()).thenReturn(true);

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
    void shouldCaptureWithFeeAndTransferCorrectAmountToConnectAccount() throws Exception {
        mockStripeCaptureAndTransfer();

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient).postRequestFor(transferRequestCaptor.capture());
        ArgumentCaptor<StripePaymentIntentCaptureRequest> captureRequestCaptor = ArgumentCaptor.forClass(StripePaymentIntentCaptureRequest.class);
        verify(gatewayClient).postRequestFor(captureRequestCaptor.capture());
        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9937"));

        assertTrue(captureResponse.isSuccessful());
        assertThat(captureResponse.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(captureResponse.getTransactionId().isPresent(), is(true));
        assertThat(captureResponse.getTransactionId().get(), is(captureGatewayRequest.getGatewayTransactionId()));
        assertThat(captureResponse.getFeeList(), hasSize(2));
        assertThat(captureResponse.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(captureResponse.getFeeList().get(0).amount(), is(58L));
        assertThat(captureResponse.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(captureResponse.getFeeList().get(1).amount(), is(5L));
    }

    @Test
    void shouldCaptureWithFee_feeCalculationShouldAlwaysRoundUp() throws Exception {
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
        assertThat(captureResponse.getFeeList(), hasSize(2));
        assertThat(captureResponse.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(captureResponse.getFeeList().get(0).amount(), is(59L));
        assertThat(captureResponse.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(captureResponse.getFeeList().get(1).amount(), is(5L));
    }

    @Test
    void shouldCaptureWithFee_feeCalculationShouldRoundUpTo1() throws Exception {
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
        assertThat(captureResponse.getFeeList(), hasSize(2));
        assertThat(captureResponse.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(captureResponse.getFeeList().get(0).amount(), is(51L));
        assertThat(captureResponse.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(captureResponse.getFeeList().get(1).amount(), is(5L));
    }

    @Test
    void shouldCaptureWithoutFee_ifCollectFeeSetToFalse() throws Exception {
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
        assertThat(response.getFeeList(), hasSize(0));
    }

    @Test
    void shouldNotCaptureIfPaymentProviderReturns4xxHttpStatusCode() throws Exception {
        String errorMessage = load(STRIPE_ERROR_RESPONSE).replace("{{code}}", "resource_missing");
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", errorMessage, SC_UNAUTHORIZED);
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenThrow(exception);
        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("error: No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    void shouldNotCaptureIfPaymentProviderReturns5xxHttpStatusCode() throws Exception {
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
    void shouldNotCaptureIfPaymentProviderReturns4XXOnTransfer() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        String errorMessage = load(STRIPE_ERROR_RESPONSE).replace("{{code}}", "resource_missing");
        GatewayErrorException exception = new GatewayErrorException("Unexpected HTTP status code 402 from gateway", errorMessage, SC_UNAUTHORIZED);
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenThrow(exception);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);
        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("No such charge: ch_123456 or something similar"));
        assertThat(response.toString(), containsString("error code: resource_missing"));
    }

    @Test
    void shouldCorrectlyCaptureUsingPaymentIntentsApi() throws Exception {
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
    void shouldNotCaptureIfPaymentProviderReturns5XXOnTransfer() throws Exception {
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

    @Test
    void shouldFailWhenCannotResolveStripeFee() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_WITHOUT_BALANCE_TRANSACTION_EXPANDED));

        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("Fee not found on Stripe charge ch_123456 when attempting to capture payment"));
    }

    @Test
    void shouldFailWhenPaymentIntentHasMultipleCharges() throws Exception {
        GatewayClient.Response gatewayCaptureResponse = mock(GatewayClient.Response.class);
        when(gatewayCaptureResponse.getEntity()).thenReturn(load(STRIPE_GET_PAYMENT_INTENT_WITH_MULTIPLE_CHARGES));

        when(gatewayClient.postRequestFor(any(StripeCaptureRequest.class))).thenReturn(gatewayCaptureResponse);

        CaptureResponse response = stripeCaptureHandler.capture(captureGatewayRequest);

        assertThat(response.isSuccessful(), is(false));
        assertThat(response.getError().isPresent(), is(true));
        assertThat(response.state(), is(nullValue()));
        assertThat(response.toString(), containsString("Expected exactly one charge associated with payment intent"));
    }

    @Test
    void shouldCaptureWithRadarAnd3dsFeesAndTransferCorrectAmountToConnectAccount() throws Exception {
        mockStripeCaptureAndTransfer();

        int chargeCreatedDate = 1629936000; //26Aug2021

        when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.50);
        when(stripeGatewayConfig.getRadarFeeInPence()).thenReturn(5);
        when(stripeGatewayConfig.getThreeDsFeeInPence()).thenReturn(10);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedDate))
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .build())
                .withEvents(List.of(aChargeEventEntity().withStatus(AUTHORISATION_3DS_REQUIRED).build(),
                        aChargeEventEntity().withStatus(CREATED).build()))
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient).postRequestFor(transferRequestCaptor.capture());
        ArgumentCaptor<StripePaymentIntentCaptureRequest> captureRequestCaptor = ArgumentCaptor.forClass(StripePaymentIntentCaptureRequest.class);
        verify(gatewayClient).postRequestFor(captureRequestCaptor.capture());

        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9885"));

        assertTrue(captureResponse.isSuccessful());

        List<Fee> feeList = captureResponse.getFeeList();
        assertThat(feeList.size(), is(3));
        assertThat(captureResponse.getFeeList(), containsInAnyOrder(
                Fee.of(TRANSACTION, 100L),
                Fee.of(RADAR, 5L),
                Fee.of(FeeType.THREE_D_S, 10L)
        ));
    }

    @Test
    void shouldCaptureWithRadarAndNo3dsFeesAndTransferCorrectAmountToConnectAccount() throws Exception {
        mockStripeCaptureAndTransfer();

        int chargeCreatedDate = 1629936000; //26Aug2021

        when(stripeGatewayConfig.getFeePercentage()).thenReturn(0.50);
        when(stripeGatewayConfig.getRadarFeeInPence()).thenReturn(5);

        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId(transactionId)
                .withAmount(10000L)
                .withCreatedDate(Instant.ofEpochSecond(chargeCreatedDate))
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .build())
                .withEvents(List.of(aChargeEventEntity().withStatus(AUTHORISATION_SUBMITTED).build(),
                        aChargeEventEntity().withStatus(CREATED).build()))
                .build();

        captureGatewayRequest = CaptureGatewayRequest.valueOf(chargeEntity);

        CaptureResponse captureResponse = stripeCaptureHandler.capture(captureGatewayRequest);

        ArgumentCaptor<StripeTransferOutRequest> transferRequestCaptor = ArgumentCaptor.forClass(StripeTransferOutRequest.class);
        verify(gatewayClient).postRequestFor(transferRequestCaptor.capture());
        ArgumentCaptor<StripePaymentIntentCaptureRequest> captureRequestCaptor = ArgumentCaptor.forClass(StripePaymentIntentCaptureRequest.class);
        verify(gatewayClient).postRequestFor(captureRequestCaptor.capture());

        assertThat(transferRequestCaptor.getValue().getGatewayOrder().getPayload(), containsString("amount=9895"));

        assertTrue(captureResponse.isSuccessful());

        List<Fee> feeList = captureResponse.getFeeList();
        assertThat(feeList.size(), is(2));
        assertThat(captureResponse.getFeeList(), containsInAnyOrder(
                Fee.of(TRANSACTION, 100L),
                Fee.of(RADAR, 5L)
        ));
    }
    
    @Test
    void shouldNotAttemptToCaptureOrTransfer_whenRetry_andChargeAlreadyCaptured_andTransferExists() throws Exception {
        ChargeEventEntity event = aValidChargeEventEntity().withChargeStatus(CAPTURE_APPROVED_RETRY).build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("pi_123")
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withEvents(List.of(event))
                .build();

        GatewayClient.Response gatewayGetPaymentIntentResponse = mock(GatewayClient.Response.class);
        when(gatewayGetPaymentIntentResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeGetPaymentIntentRequest.class))).thenReturn(gatewayGetPaymentIntentResponse);

        GatewayClient.Response gatewaySearchTransactionsResponse = mock(GatewayClient.Response.class);
        when(gatewaySearchTransactionsResponse.getEntity()).thenReturn(load(STRIPE_SEARCH_TRANSFERS_FOR_CAPTURED_PAYMENT_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeSearchTransfersRequest.class))).thenReturn(gatewaySearchTransactionsResponse);

        CaptureResponse response = stripeCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().get(), is("pi_123"));
        assertThat(response.getFeeList(), hasSize(2));
        assertThat(response.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(response.getFeeList().get(0).amount(), is(58L));
        assertThat(response.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(response.getFeeList().get(1).amount(), is(5L));
        
        verify(gatewayClient, times(2)).getRequestFor(gatewayClientGetRequestArgumentCaptor.capture());
        List<GatewayClientGetRequest> getRequests = gatewayClientGetRequestArgumentCaptor.getAllValues();
        
        StripeGetPaymentIntentRequest getPaymentIntentRequest = (StripeGetPaymentIntentRequest) getRequests.get(0);
        assertThat(getPaymentIntentRequest.getUrl().getPath(), containsString("/v1/payment_intents/" + chargeEntity.getGatewayTransactionId()));
        
        StripeSearchTransfersRequest stripeSearchTransfersRequest = (StripeSearchTransfersRequest) getRequests.get(1);
        assertThat(stripeSearchTransfersRequest.getQueryParams(), hasEntry("transfer_group", chargeEntity.getExternalId()));
        
        verify(gatewayClient, never()).postRequestFor(any());
    }

    @Test
    void shouldDoTransfer_whenRetry_andChargeAlreadyCaptured_andTransferDoesNotExist() throws Exception {
        ChargeEventEntity event = aValidChargeEventEntity().withChargeStatus(CAPTURE_APPROVED_RETRY).build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("pi_123")
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withEvents(List.of(event))
                .build();

        GatewayClient.Response gatewayGetPaymentIntentResponse = mock(GatewayClient.Response.class);
        when(gatewayGetPaymentIntentResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeGetPaymentIntentRequest.class))).thenReturn(gatewayGetPaymentIntentResponse);

        GatewayClient.Response gatewaySearchTransactionsResponse = mock(GatewayClient.Response.class);
        when(gatewaySearchTransactionsResponse.getEntity()).thenReturn(load(STRIPE_SEARCH_TRANSFERS_EMPTY_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeSearchTransfersRequest.class))).thenReturn(gatewaySearchTransactionsResponse);

        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenReturn(gatewayTransferResponse);
        
        CaptureResponse response = stripeCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().get(), is("pi_123"));
        assertThat(response.getFeeList(), hasSize(2));
        assertThat(response.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(response.getFeeList().get(0).amount(), is(58L));
        assertThat(response.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(response.getFeeList().get(1).amount(), is(5L));
        
        verify(gatewayClient).postRequestFor(stripeTransferOutRequestArgumentCaptor.capture());
        StripeTransferOutRequest transferOutRequest = stripeTransferOutRequestArgumentCaptor.getValue();
        assertThat(transferOutRequest.getGatewayOrder().getPayload(), containsString("amount=9937"));

        verify(gatewayClient, never()).postRequestFor(any(StripeCaptureRequest.class));
    }

    @Test
    void shouldCaptureAndTransfer_whenRetry_andChargeNotAlreadyCaptured() throws Exception {
        ChargeEventEntity event = aValidChargeEventEntity().withChargeStatus(CAPTURE_APPROVED_RETRY).build();
        ChargeEntity chargeEntity = aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccount)
                .withTransactionId("pi_123")
                .withAmount(10000L)
                .withGatewayAccountCredentialsEntity(aGatewayAccountCredentialsEntity()
                        .withCredentials(Map.of("stripe_account_id", "stripe_account_id"))
                        .withPaymentProvider(STRIPE.getName())
                        .withState(ACTIVE)
                        .build())
                .withEvents(List.of(event))
                .build();

        GatewayClient.Response gatewayGetPaymentIntentResponse = mock(GatewayClient.Response.class);
        when(gatewayGetPaymentIntentResponse.getEntity()).thenReturn(load(STRIPE_PAYMENT_INTENT_CAPTURE_SUCCESS_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeGetPaymentIntentRequest.class))).thenReturn(gatewayGetPaymentIntentResponse);

        GatewayClient.Response gatewaySearchTransactionsResponse = mock(GatewayClient.Response.class);
        when(gatewaySearchTransactionsResponse.getEntity()).thenReturn(load(STRIPE_SEARCH_TRANSFERS_EMPTY_RESPONSE));
        when(gatewayClient.getRequestFor(any(StripeSearchTransfersRequest.class))).thenReturn(gatewaySearchTransactionsResponse);

        GatewayClient.Response gatewayTransferResponse = mock(GatewayClient.Response.class);
        when(gatewayTransferResponse.getEntity()).thenReturn(load(STRIPE_TRANSFER_RESPONSE));
        when(gatewayClient.postRequestFor(any(StripeTransferOutRequest.class))).thenReturn(gatewayTransferResponse);

        CaptureResponse response = stripeCaptureHandler.capture(CaptureGatewayRequest.valueOf(chargeEntity));
        assertThat(response.isSuccessful(), is(true));
        assertThat(response.state(), is(CaptureResponse.ChargeState.COMPLETE));
        assertThat(response.getTransactionId().get(), is("pi_123"));
        assertThat(response.getFeeList(), hasSize(2));
        assertThat(response.getFeeList().get(0).feeType(), is(TRANSACTION));
        assertThat(response.getFeeList().get(0).amount(), is(58L));
        assertThat(response.getFeeList().get(1).feeType(), is(RADAR));
        assertThat(response.getFeeList().get(1).amount(), is(5L));

        verify(gatewayClient, never()).postRequestFor(any(StripeCaptureRequest.class));
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
