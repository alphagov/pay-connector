package uk.gov.pay.connector.charge.resource;

import io.dropwizard.setup.Environment;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ChargesApiResourceResendConfirmationEmailIT.ConnectorAppWithCustomInjector.class, config = "config/test-it-config.yaml",
        configOverrides = {@ConfigOverride(key = "notifyConfig.emailNotifyEnabled", value = "true")})
public class ChargesApiResourceResendConfirmationEmailIT {

    @DropwizardTestContext
    protected TestContext testContext;

    private static NotifyClientFactory notifyClientFactory = mock(NotifyClientFactory.class);
    private static NotificationClient notificationClient = mock(NotificationClient.class);
    private static DatabaseTestHelper databaseTestHelper;
    
    private String accountId;
    private final String emailAddress = "a@b.com";

    @BeforeClass
    public static void before() {
        when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
    }

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());
        addGatewayAccount();
    }   

    @Test
    public void shouldReturn204WhenEmailSuccessfullySent() throws Exception {
        String transactionId = String.valueOf(RandomUtils.nextInt());
        String paymentProvider = "sandbox";

        ChargeUtils.ExternalChargeId chargeId = createNewCharge(transactionId, paymentProvider);

        var notificationId = UUID.randomUUID();
        var mockResponse = mock(SendEmailResponse.class);
        when(mockResponse.getNotificationId()).thenReturn(notificationId);
        when(notificationClient.sendEmail(any(), eq(emailAddress), anyMap(), eq(null), any())).thenReturn(mockResponse);

        given().port(testContext.getPort())
                .body("")
                .contentType(APPLICATION_JSON)
                .post(String.format("/v1/api/accounts/%s/charges/%s/resend-confirmation-email", accountId, chargeId))
                .then()
                .statusCode(204);
    }
    
    private ChargeUtils.ExternalChargeId createNewCharge(String transactionId, String paymentProvider) {
        return ChargeUtils.createNewChargeWithAccountId(
                ChargeStatus.CREATED,
                transactionId,
                accountId,
                databaseTestHelper,
                emailAddress,
                paymentProvider);
    }

    private void addGatewayAccount() {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("sandbox")
                .build());
        databaseTestHelper.addEmailNotification(Long.valueOf(accountId), "a template", true, PAYMENT_CONFIRMED);
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
        protected NotifyClientFactory getNotifyClientFactory(ConnectorConfiguration connectorConfiguration) {
            return notifyClientFactory;
        }
    }

}
