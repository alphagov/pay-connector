package uk.gov.pay.connector.queue.tasks;

import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.queue.tasks.handlers.DeleteStoredPaymentDetailsTaskHandler;
import uk.gov.pay.connector.util.AddAgreementParams;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.AddPaymentInstrumentParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml", withDockerSQS = true)
public class DeleteStoredPaymentDetailsTaskHandlerIT {

    @DropwizardTestContext
    private TestContext testContext;
    private DatabaseTestHelper databaseTestHelper;
    private AddGatewayAccountCredentialsParams accountCredentialsParams;
    private String stripeAccountId = "stripe-account-id";
    private String accountId = String.valueOf(nextLong());
    
    @Before
    public void setUp() {
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

    @Test
    public void shouldLogErrorResponseWhenDeleteStoredPaymentDetailsNotImplementedForPaymentProvider() {
        DeleteStoredPaymentDetailsTaskHandler taskHandler = testContext.getInstanceFromGuiceContainer(DeleteStoredPaymentDetailsTaskHandler.class);
        String agreementExternalId = "test-agreement-123";
        String paymentInstrumentExternalId = "test-paymentInstrument-123";
        setupAgreementAndPaymentInstrument(agreementExternalId, paymentInstrumentExternalId);
        var thrown = assertThrows(RuntimeException.class, () -> taskHandler.process(agreementExternalId, paymentInstrumentExternalId));
        assertThat(thrown.getMessage(), Matchers.is("Delete Stored Payment Details is not implemented for this payment provider"));
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }
    
    private void setupAgreementAndPaymentInstrument(String agreementExternalId, String paymentInstrumentExternalId) {
        Long paymentInstrumentId = RandomUtils.nextLong();
        AddPaymentInstrumentParams paymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(paymentInstrumentId)
                .withExternalPaymentInstrumentId(paymentInstrumentExternalId)
                .build();
        databaseTestHelper.addPaymentInstrument(paymentInstrumentParams);
        
        AddAgreementParams agreementParams = anAddAgreementParams()
                .withGatewayAccountId(accountId)
                .withExternalAgreementId(agreementExternalId)
                .withPaymentInstrumentId(paymentInstrumentId)
                .build();
        databaseTestHelper.addAgreement(agreementParams);
    }
}
