package uk.gov.pay.connector.it.resources.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stripe.exception.ApiException;
import com.stripe.exception.CardException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import io.dropwizard.core.setup.Environment;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

public class RefundReversalResourceIT {
    private static final StripeSdkClientFactory mockStripeSdkClientFactory = mock(StripeSdkClientFactory.class);
    private static RandomIdGenerator mockRandomIdGenerator = mock(RandomIdGenerator.class);

    // App must be instantiated after mock is set
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(RefundReversalResourceIT.ConnectorAppWithCustomInjector.class);
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("stripe", app.getLocalPort(), app.getDatabaseTestHelper());
    private static final StripeSdkClient mockStripeSdkClient = mock(StripeSdkClient.class);
    @Mock
    private com.stripe.model.Charge mockedStripeCharge = Mockito.mock(com.stripe.model.Charge.class);
    @Mock
    private com.stripe.model.Account mockedStripeAccount = Mockito.mock(com.stripe.model.Account.class);


    private static final String CHARGE_EXTERNAL_ID = "charge-external-id";
    private static final String REFUND_EXTERNAL_ID = "refund-external-id";
    private static final String STRIPE_REFUND_ID = "stripe-refund-id";
    private LedgerTransaction charge;
    private LedgerTransaction refund;
    private final Refund mockStripeRefund = mock(Refund.class);


    @BeforeAll
    public static void before() {
        when(mockStripeSdkClientFactory.getInstance()).thenReturn(mockStripeSdkClient);
    }

    @BeforeEach
    public void setUp() {
        charge = aValidLedgerTransaction()
                .withExternalId(CHARGE_EXTERNAL_ID)
                .withGatewayAccountId(Long.parseLong(testBaseExtension.getAccountId()))
                .withAmount(1000L)
                .withRefundSummary(new ChargeResponse.RefundSummary())
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .build();

        refund = aValidLedgerTransaction()
                .withExternalId(REFUND_EXTERNAL_ID)
                .withGatewayAccountId(Long.parseLong(testBaseExtension.getAccountId()))
                .withParentTransactionId(CHARGE_EXTERNAL_ID)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId(STRIPE_REFUND_ID)
                .build();

        when(mockRandomIdGenerator.random13ByteHexGenerator()).thenReturn("random123");

    }

    @AfterEach
    void tearDown() {
        reset(mockStripeSdkClient);
    }

    @Test
    void shouldSuccessfullyDoTransferWhenRefundIsInFailedState() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);
        app.getStripeMockClient().mockRefundSuccess();

        when(mockStripeRefund.getStatus()).thenReturn("failed");

        when(mockStripeRefund.getChargeObject()).thenReturn(mockedStripeCharge);

        when(mockedStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockedStripeCharge.getOnBehalfOfObject()).thenReturn(mockedStripeAccount);
        when(mockedStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockedStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");

        ValidatableResponse response = given().port(app.getLocalPort())
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then();
        response.statusCode(Response.Status.OK.getStatusCode());
    }
    
    @Test
    void shouldReturnInternalErrorWhenRefundNotFoundFromStripe() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenThrow(WebApplicationException.class);

        given().port(app.getLocalPort())
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then()
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    @Test
    void shouldReturnErrorForStripeException() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        Map<String, Object> transferRequest = Map.of(
                "destination", "acct_jdsa7789d",
                "amount", 100L,
                "metadata", Map.of(
                        "stripeChargeId", "ch_sdkhdg887s",
                        "correctionPaymentId", "random123"
                ),
                "currency", "GBP",
                "transferGroup", "abc",
                "expand", List.of("balance_transaction", "destination_payment")
        );

        when(mockStripeRefund.getStatus()).thenReturn("failed");

        when(mockStripeRefund.getChargeObject()).thenReturn(mockedStripeCharge);
        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);

        when(mockedStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockedStripeCharge.getOnBehalfOfObject()).thenReturn(mockedStripeAccount);
        when(mockedStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockedStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");


        doThrow(new ApiException("error connecting to Stripe", "request_123", null, 500, null))
                .when(mockStripeSdkClient).createTransfer(transferRequest, false);

        given().port(app.getLocalPort())
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then()
                .body("message", is("There was an error trying to create transfer with id: " + REFUND_EXTERNAL_ID + " from Stripe: " + "error connecting to Stripe; request-id: request_123"))
                .statusCode(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()
                );
    }
    
    @Test
    void shouldReturnErrorWhenInsufficientFunds() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        Map<String, Object> transferRequest = Map.of(
                "destination", "acct_jdsa7789d",
                "amount", 100L,
                "metadata", Map.of(
                        "stripeChargeId", "ch_sdkhdg887s",
                        "correctionPaymentId", "random123"
                ),
                "currency", "GBP",
                "transferGroup", "abc",
                "expand", List.of("balance_transaction", "destination_payment")
        );

        when(mockStripeRefund.getStatus()).thenReturn("failed");

        when(mockStripeRefund.getChargeObject()).thenReturn(mockedStripeCharge);
        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);

        when(mockedStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockedStripeCharge.getOnBehalfOfObject()).thenReturn(mockedStripeAccount);
        when(mockedStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockedStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");

        StripeError stripeError = new StripeError();
        stripeError.setMessage("insufficient funds");
        stripeError.setCode("insufficient_funds_error");
        stripeError.setDeclineCode("insufficient_funds");

        CardException cardexception = new CardException(
                "error with stripe because of insufficient funds",
                "req_12345",
                "insufficient_funds_error",
                "param_value",
                "insufficient_funds",
                charge.toString(),
                400,
                null);

        cardexception.setStripeError(stripeError);
        doThrow(cardexception).when(mockStripeSdkClient).createTransfer(transferRequest, false);

        given().port(app.getLocalPort())
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then()
                .body("message[0]", is("Transfer failed due to insufficient funds for refund with " + REFUND_EXTERNAL_ID + " error with stripe because of insufficient funds; code: insufficient_funds_error; request-id: req_12345"))
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }


    @Test
    void shouldReturnErrorWhenDuplicateIdepmpotencyKeys() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        Map<String, Object> transferRequest = Map.of(
                "destination", "acct_jdsa7789d",
                "amount", 100L,
                "metadata", Map.of(
                        "stripeChargeId", "ch_sdkhdg887s",
                        "correctionPaymentId", "random123"
                ),
                "currency", "GBP",
                "transferGroup", "abc",
                "expand", List.of("balance_transaction", "destination_payment")
        );

        when(mockStripeRefund.getStatus()).thenReturn("failed");

        when(mockStripeRefund.getChargeObject()).thenReturn(mockedStripeCharge);
        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);

        when(mockedStripeCharge.getId()).thenReturn("ch_sdkhdg887s");
        when(mockedStripeCharge.getOnBehalfOfObject()).thenReturn(mockedStripeAccount);
        when(mockedStripeAccount.getId()).thenReturn("acct_jdsa7789d");
        when(mockedStripeCharge.getTransferGroup()).thenReturn("abc");
        when(mockStripeRefund.getAmount()).thenReturn(100L);
        when(mockStripeRefund.getCurrency()).thenReturn("GBP");


        StripeError stripeError = new StripeError();
        stripeError.setMessage("idempotency error");
        stripeError.setCode("idempotency_error");
        stripeError.setType("idempotency_error");


        IdempotencyException idempotencyException = new IdempotencyException("error because idempotency key already used",
                "request_123", null, 400
        );
        idempotencyException.setStripeError(stripeError);

        doThrow(idempotencyException)
                .when(mockStripeSdkClient).createTransfer(transferRequest, false);


        given().port(app.getLocalPort())
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", testBaseExtension.getAccountId())
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then()
                .body("message[0]", is("failed transfer due to idempotency error for refund with " + REFUND_EXTERNAL_ID + " error because idempotency key already used; request-id: request_123"))
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());
    }

    public static class ConnectorAppWithCustomInjector extends ConnectorApp {
        @Override
        protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
            return new ConnectorModuleWithOverrides(configuration, environment);
        }
    }

    private static class ConnectorModuleWithOverrides extends ConnectorModule {

        public ConnectorModuleWithOverrides(ConnectorConfiguration configuration, Environment environment) {
            super(configuration, environment);
        }

        @Override
        protected StripeSdkClientFactory getStripeSdkClientFactory(ConnectorConfiguration connectorConfiguration) {
            return mockStripeSdkClientFactory;
        }

        @Override
        protected RandomIdGenerator getRandomIdGenerator() {
            return mockRandomIdGenerator;
        }

    }
}
