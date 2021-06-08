package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.notify.NotificationClient;

import java.time.ZonedDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_OUT_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewChargeWithAccountId;
import static uk.gov.pay.connector.it.util.NotificationUtils.epdqNotificationPayload;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = SendRefundEmailIT.ConnectorAppWithCustomInjector.class, config = "config/test-it-config.yaml", 
        configOverrides = {@ConfigOverride(key = "notifyConfig.emailNotifyEnabled", value = "true")})
public class SendRefundEmailIT {
    private static final String EPDQ_IP_ADDRESS = "4.3.2.1";

    @DropwizardTestContext
    protected TestContext testContext;
    
    private static NotifyClientFactory notifyClientFactory = mock(NotifyClientFactory.class);
    private static NotificationClient notificationClient = mock(NotificationClient.class);
    private static DatabaseTestHelper databaseTestHelper;
    private static Map<String, String> credentials = ImmutableMap.of(
            CREDENTIALS_MERCHANT_ID, "merchant-id",
            CREDENTIALS_USERNAME, "test-user",
            CREDENTIALS_PASSWORD, "test-password",
            CREDENTIALS_SHA_IN_PASSPHRASE, "test-sha-in-passphrase",
            CREDENTIALS_SHA_OUT_PASSPHRASE, "test-sha-out-passphrase"
    );
    private String accountId;

    @BeforeClass
    public static void before() {
        when(notifyClientFactory.getInstance()).thenReturn(notificationClient);
    }
    
    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();
        accountId = String.valueOf(RandomUtils.nextInt());
    }

    @Test
    public void shouldSendEmailFollowingASuccessfulRefund() throws Exception {
        addGatewayAccount();

        String transactionId = String.valueOf(RandomUtils.nextInt());
        String payIdSub = "2";
        String refundExternalId = "999999";

        ChargeUtils.ExternalChargeId chargeId = createNewChargeWithAccountId(CAPTURED, transactionId, accountId, databaseTestHelper);
        databaseTestHelper.addRefund(refundExternalId,
                100,  REFUND_SUBMITTED, transactionId + "/" + payIdSub,
                ZonedDateTime.now(), chargeId.toString());

        given().port(testContext.getPort())
                .header("X-Forwarded-For", EPDQ_IP_ADDRESS)
                .body(epdqNotificationPayload(transactionId, payIdSub, "8", credentials.get(CREDENTIALS_SHA_OUT_PASSPHRASE)))
                .contentType(APPLICATION_FORM_URLENCODED)
                .post("/v1/api/notifications/epdq");

        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), isNull());
    }

    private void addGatewayAccount() {
        databaseTestHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(accountId)
                .withPaymentGateway("epdq")
                .withCredentials(credentials)
                .build());
        databaseTestHelper.addEmailNotification(Long.valueOf(accountId), "a template", true, REFUND_ISSUED);
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
