package uk.gov.pay.connector.queue.tasks;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.queue.tasks.handlers.CollectFeesForFailedPaymentsTaskHandler;
import uk.gov.pay.connector.queue.tasks.model.PaymentTaskData;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CollectFeesForFailedPaymentsTaskHandlerIT {

    @DropwizardTestContext
    private TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;

    private WireMockServer wireMockServer;

    private StripeMockClient stripeMockClient;

    private AddGatewayAccountCredentialsParams accountCredentialsParams;

    private String stripeAccountId = "stripe-account-id";
    private String accountId = "555";
    private String paymentIntentId = "stripe-payment-intent-id";

    @Before
    public void setUp() {
        wireMockServer = testContext.getWireMockServer();
        stripeMockClient = new StripeMockClient(wireMockServer);

        databaseTestHelper = testContext.getDatabaseTestHelper();

        accountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(Long.parseLong(accountId))
                .withState(ACTIVE)
                .withCredentials(Map.of("stripe_account_id", stripeAccountId))
                .build();

        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway(STRIPE.getName())
                .withGatewayAccountCredentials(List.of(accountCredentialsParams))
                .withIntegrationVersion3ds(1)
                .build());
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void shouldPersistFees() throws Exception {
        long chargeId = nextInt();
        String chargeExternalId = RandomIdGenerator.newId();
        var paymentTaskData = new PaymentTaskData(chargeExternalId);
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withPaymentProvider(STRIPE.getName())
                .withGatewayAccountId(accountId)
                .withAmount(10000)
                .withStatus(ChargeStatus.AUTHORISATION_REJECTED)
                .withGatewayCredentialId(accountCredentialsParams.getId())
                .withTransactionId(paymentIntentId)
                .build());

        stripeMockClient.mockGet3DSAuthenticatedPaymentIntent(paymentIntentId);
        stripeMockClient.mockTransferSuccess();

        CollectFeesForFailedPaymentsTaskHandler taskHandler = testContext.getInstanceFromGuiceContainer(CollectFeesForFailedPaymentsTaskHandler.class);
        taskHandler.collectAndPersistFees(paymentTaskData);

        List<Map<String, Object>> fees = databaseTestHelper.getFeesByChargeId(chargeId);
        assertThat(fees, hasSize(2));
        assertThat(fees, containsInAnyOrder(
                allOf(
                        hasEntry("fee_type", (Object)"radar"),
                        hasEntry("amount_collected", 5L)
                ),
                allOf(
                        hasEntry("fee_type", (Object)"three_ds"),
                        hasEntry("amount_collected", 6L)
                )
        ));
    }
}
