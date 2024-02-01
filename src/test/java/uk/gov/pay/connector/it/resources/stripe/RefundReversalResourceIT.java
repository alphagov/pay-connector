package uk.gov.pay.connector.it.resources.stripe;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import io.dropwizard.setup.Environment;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.model.ChargeResponse;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClient;
import uk.gov.pay.connector.gateway.stripe.StripeSdkClientFactory;
import uk.gov.pay.connector.it.SendRefundEmailIT;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;


import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;


@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = RefundReversalResourceIT.ConnectorAppWithCustomInjector.class, config = "config/test-it-config.yaml")
public class RefundReversalResourceIT {

    @DropwizardTestContext
    protected TestContext testContext;

    private static StripeSdkClientFactory mockStripeSdkClientFactory = mock(StripeSdkClientFactory.class);
    private static StripeSdkClient mockStripeSdkClient = mock(StripeSdkClient.class);

    private static final long GATEWAY_ACCOUNT_ID = 42;
    private static final String CHARGE_EXTERNAL_ID = "charge-external-id";
    private static final String REFUND_EXTERNAL_ID = "refund-external-id";
    private static final String STRIPE_REFUND_ID = "stripe-refund-id";

    private final String accountId = String.valueOf(GATEWAY_ACCOUNT_ID);

    private static DatabaseTestHelper databaseTestHelper;
    

    private WireMockServer wireMockServer;
    private LedgerStub ledgerStub;
    
    private LedgerTransaction charge;
    private LedgerTransaction refund;
    
    private Refund mockStripeRefund = mock(Refund.class);

    private AddGatewayAccountCredentialsParams accountCredentialsParams;
    
    @BeforeClass
    public static void before() {
        when(mockStripeSdkClientFactory.getInstance()).thenReturn(mockStripeSdkClient);
    }

    @Before
    public void setUp() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        wireMockServer = testContext.getWireMockServer();

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(PaymentGatewayName.STRIPE.getName())
                .withCredentials(Map.of())
                .build());

        ledgerStub = new LedgerStub(wireMockServer);

        charge = aValidLedgerTransaction()
                .withExternalId(CHARGE_EXTERNAL_ID)
                .withGatewayAccountId(GATEWAY_ACCOUNT_ID)
                .withCredentialExternalId("123")
                .withAmount(1000L)
                .withRefundSummary(new ChargeResponse.RefundSummary())
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .build();

        refund = aValidLedgerTransaction()
                .withExternalId(REFUND_EXTERNAL_ID)
                .withGatewayAccountId(GATEWAY_ACCOUNT_ID)
                .withParentTransactionId(CHARGE_EXTERNAL_ID)
                .withCredentialExternalId("123")
                .withAmount(300L)
                .withStatus(ExternalRefundStatus.EXTERNAL_SUCCESS.getStatus())
                .withPaymentProvider(PaymentGatewayName.STRIPE.getName())
                .withGatewayTransactionId(STRIPE_REFUND_ID)
                .build();
        

        
//        ledgerStub = new LedgerStub(wireMockServer);
       
        ///Mock 
    }

    @Test
    public void shouldSuccessfullyRefund_usingChargeId() throws JsonProcessingException, StripeException {

        ledgerStub.returnLedgerTransaction(CHARGE_EXTERNAL_ID, charge);
        ledgerStub.returnLedgerTransaction(REFUND_EXTERNAL_ID, refund);

        when(mockStripeSdkClient.getRefund(eq(STRIPE_REFUND_ID), anyBoolean())).thenReturn(mockStripeRefund);
        
        when(mockStripeRefund.getStatus()).thenReturn("failed");

//        var gatewayAccountParams = anAddGatewayAccountParams()
//                .withAccountId(accountId)
//                .withPaymentGateway("stripe")
//                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
//                .build();
   
//        long amount = 10L;
//        stripeMockClient.mockTransferSuccess();
//        stripeMockClient.mockRefund();

        //mock ledger to get the charge and refund;
        //mock charge too
        // stripe mockout might not work becasue
     

        ValidatableResponse response = given().port(testContext.getPort())
                //.body(refundPayload)
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON)
                .post("/v1/api/accounts/{gatewayAccountId}/charges/{chargeId}/refunds/{refundId}/reverse-failed"
                        .replace("{gatewayAccountId}", accountId)
                        .replace("{chargeId}", CHARGE_EXTERNAL_ID)
                        .replace("{refundId}", REFUND_EXTERNAL_ID))
                .then()
                .statusCode(200);

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
