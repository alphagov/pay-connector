package uk.gov.pay.connector.it.resources.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import io.dropwizard.setup.Environment;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
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

import javax.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;

public class RefundReversalResourceIT {
    private static final StripeSdkClientFactory mockStripeSdkClientFactory = mock(StripeSdkClientFactory.class);
    
    // App must be instantiated after mock is set
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(RefundReversalResourceIT.ConnectorAppWithCustomInjector.class);
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("stripe", app.getLocalPort(), app.getDatabaseTestHelper());
    private static final StripeSdkClient mockStripeSdkClient = mock(StripeSdkClient.class);
    
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
    }

    @Test
    public void shouldSuccessfullyGetRefundFromStripe() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);
        app.getStripeMockClient().mockRefund();
        
        when(mockStripeRefund.getStatus()).thenReturn("failed");
        
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
    public void shouldReturnInternalErrorWhenRefundNotFoundFromStripe() throws JsonProcessingException, StripeException {
        app.getLedgerStub().returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        app.getLedgerStub().returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(null);
        app.getStripeMockClient().mockRefund();

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
    }
}

