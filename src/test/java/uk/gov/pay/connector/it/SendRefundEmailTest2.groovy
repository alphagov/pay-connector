package uk.gov.pay.connector.it

import io.dropwizard.db.DataSourceFactory
import io.dropwizard.testing.DropwizardTestSupport
import org.apache.commons.lang.math.RandomUtils
import org.skife.jdbi.v2.DBI
import spock.lang.Specification
import uk.gov.pay.commons.testing.db.PostgresDockerRule
import uk.gov.pay.connector.app.ConnectorApp
import uk.gov.pay.connector.app.ConnectorModule
import uk.gov.pay.connector.usernotification.govuknotify.NotifyClientFactory
import uk.gov.pay.connector.util.DatabaseTestHelper
import uk.gov.service.notify.NotificationClient

import static com.jayway.restassured.RestAssured.given
import static io.dropwizard.testing.ConfigOverride.config
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED
import static org.mockito.ArgumentMatchers.*
import static org.mockito.Mockito.*
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.*
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewChargeWithAccountId
import static uk.gov.pay.connector.it.util.ChargeUtils.createNewRefund
import static uk.gov.pay.connector.it.util.NotificationUtils.epdqNotificationPayload
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED

class SendRefundEmailTest2 extends Specification {

    private static def notifyClientFactory = mock(NotifyClientFactory.class)
    private static def notificationClient = mock(NotificationClient.class)
    private static def credentials = [
            (CREDENTIALS_MERCHANT_ID) : 'merchant-id',
            (CREDENTIALS_USERNAME): 'test-user',
            (CREDENTIALS_PASSWORD): 'test-password',
            (CREDENTIALS_SHA_IN_PASSPHRASE): 'test-sha-in-passphrase',
            (CREDENTIALS_SHA_OUT_PASSPHRASE): 'test-sha-out-passphrase'
    ]

    def "should send email following a successful refund"() {
        when: "dropwizard has started up"
//        ConnectorModule connectorModule = [ getNotifyClientFactory: { notifyClientFactory } ] as ConnectorModule
//        ConnectorApp connectorApp = [ getModule: { connectorModule } ] as ConnectorApp
        def postgresDockerRule = new PostgresDockerRule()
        ConnectorModule.metaClass.getNotifyClientFactory = { notifyClientFactory }
        ConnectorApp.metaClass.getModule = { connectorModule }
        DropwizardTestSupport app = new DropwizardTestSupport<>(ConnectorApp.class, resourceFilePath("config/test-it-config.yaml"),
                config("notifyConfig.emailNotifyEnabled", "true"), config("database.url", postgresDockerRule.getConnectionUrl()), config("database.user", postgresDockerRule.getUsername()), config("database.password", postgresDockerRule.getPassword()))
        app.before();
        app.getApplication().run("db", "drop-all", "--confirm-delete-everything", resourceFilePath("config/test-it-config.yaml"));
        app.getApplication().run("db", "migrate", resourceFilePath("config/test-it-config.yaml"));
        DataSourceFactory dataSourceFactory = app.getConfiguration().getDataSourceFactory()
        DatabaseTestHelper databaseTestHelper = new DatabaseTestHelper(new DBI(dataSourceFactory.getUrl(), dataSourceFactory.getUser(), dataSourceFactory.getPassword()));
        String accountId = String.valueOf(RandomUtils.nextInt())
        addGatewayAccount(databaseTestHelper, accountId)
        when(notifyClientFactory.getInstance()).thenReturn(notificationClient)

        and: "charge with refund exists"
        String transactionId = String.valueOf(RandomUtils.nextInt())
        String payIdSub = "2"
        String refundExternalId = "999999"
        long chargeId = createNewChargeWithAccountId(CAPTURED, transactionId, accountId, databaseTestHelper).chargeId
        createNewRefund(REFUND_SUBMITTED, chargeId, refundExternalId, transactionId + "/" + payIdSub, 100, databaseTestHelper)

        and: "a refund notification is POSTed"
        given().port(app.localPort)
                .body(epdqNotificationPayload(transactionId, payIdSub, "8", credentials.get(CREDENTIALS_SHA_OUT_PASSPHRASE)))
                .contentType(APPLICATION_FORM_URLENCODED)
                .post("/v1/api/notifications/epdq")

        then: "then"
        verify(notificationClient).sendEmail(anyString(), anyString(), anyMap(), isNull())
    }

    private void addGatewayAccount(databaseTestHelper, accountId) {
        databaseTestHelper.addGatewayAccount(accountId, "epdq", credentials)
        databaseTestHelper.addEmailNotification(Long.valueOf(accountId), "a template", true, REFUND_ISSUED)
    }
}
