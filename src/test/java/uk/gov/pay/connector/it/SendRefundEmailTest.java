package uk.gov.pay.connector.it;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ConfigOverride;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.AppRule;
import uk.gov.pay.connector.rules.AppWithPostgresRule;
import uk.gov.pay.connector.rules.DropwizardAppRule;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.service.notify.NotificationClient;

import java.util.Map;

import static com.jayway.restassured.RestAssured.given;
import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
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
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewRefund;
import static uk.gov.pay.connector.it.util.EpdqNotificationUtils.notificationPayloadForTransaction;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;

public class SendRefundEmailTest {

    @ClassRule
    public static DropwizardApp app = new DropwizardApp(config("notifyConfig.emailNotifyEnabled", "true"));

    private static NotifyClientFactory notifyClientFactory = mock(NotifyClientFactory.class);
    private static NotificationClient notificationClient = mock(NotificationClient.class);
    private static DatabaseTestHelper databaseTestHelper;
    private Map<String, String> credentials = ImmutableMap.of(
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
        databaseTestHelper = app.getDatabaseTestHelper();
    }
    
    @Before
    public void setup() {
        accountId = String.valueOf(RandomUtils.nextInt());
    }

    @Test
    public void shouldSendEmailFollowingASuccessfulRefund() throws Exception {
        addGatewayAccount();

        String transactionId = String.valueOf(RandomUtils.nextInt());
        String payIdSub = "2";
        String refundExternalId = "999999";

        long chargeId = createNewChargeWithAccountId(CAPTURED, transactionId, accountId, databaseTestHelper).chargeId;
        createNewRefund(REFUND_SUBMITTED, chargeId, refundExternalId, transactionId + "/" + payIdSub, 100, databaseTestHelper);

        given().port(app.getLocalPort())
                .body(notificationPayloadForTransaction(transactionId, payIdSub, "8", credentials.get(CREDENTIALS_SHA_OUT_PASSPHRASE)))
                .contentType(APPLICATION_FORM_URLENCODED)
                .post("/v1/api/notifications/epdq");

        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), isNull());
    }

    private void addGatewayAccount() {
        databaseTestHelper.addGatewayAccount(accountId, "epdq", credentials);
        databaseTestHelper.addEmailNotification(Long.valueOf(accountId), "a template", true, REFUND_ISSUED);
    }

    public static class DropwizardApp extends AppWithPostgresRule {

        public DropwizardApp(ConfigOverride... configOverrides) {
            super(configOverrides);
        }

        @Override
        protected AppRule<ConnectorConfiguration> newApplication(String configPath, ConfigOverride... configOverrides) {
            return new DropwizardAppRule<>(ConnectorAppWithCustomInjector.class, configPath, configOverrides);
        }
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
