package uk.gov.pay.connector.it.resources;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.resources.epdq.EpdqCardResourceITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqChargeCancelResourceITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqNotificationResourceITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqRefundITest;
import uk.gov.pay.connector.it.resources.sandbox.SandboxNotificationResourceITest;
import uk.gov.pay.connector.it.resources.sandbox.SandboxRefundITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayCardResourceITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayNotificationResourceWithAccountSpecificAuthITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayRefundITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayCardResourceITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayChargeCancelResourceITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayNotificationResourceITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayRefundITest;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import static io.dropwizard.testing.ConfigOverride.config;

@RunWith(Suite.class)

@Suite.SuiteClasses(value = {
        EpdqCardResourceITest.class,
        EpdqChargeCancelResourceITest.class,
        EpdqNotificationResourceITest.class,
        EpdqRefundITest.class,
        SandboxNotificationResourceITest.class,
        SandboxRefundITest.class,
        SmartpayCardResourceITest.class,
        SmartpayNotificationResourceWithAccountSpecificAuthITest.class,
        SmartpayRefundITest.class,
        WorldpayCardResourceITest.class,
        WorldpayChargeCancelResourceITest.class,
        WorldpayNotificationResourceITest.class,
        WorldpayRefundITest.class,
        CardAuthoriseResourceITest.class,
        CardCaptureResourceITest.class,
        CardTypesResourceITest.class,
        ChargeCancelFrontendResourceITest.class,
        ChargeCancelResourceITest.class,
        ChargeEventsResourceITest.class,
        ChargeExpiryResourceITest.class,
        ChargesApiResourceITest.class,
        ChargesFrontendResourceITest.class,
        DatabaseConnectionITest.class,
        EmailNotificationResourceITest.class,
        GatewayAccountFrontendResourceITest.class,
        GatewayAccountResourceITest.class,
        GatewayAccountResourceTestBase.class,
        HealthCheckResourceITest.class,
        SearchChargesByDateResourceITest.class,
        SecurityTokensResourceITest.class,
        TransactionsApiResourceITest.class,
        TransactionsSummaryResourceITest.class
})
public class IntegrationTestSuite {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTestSuite.class);

    private static int port = PortFactory.findFreePort();

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("epdq.urls.test", "http://localhost:" + port + "/epdq"));

    public static DropwizardAppWithPostgresRule getApp() {
        return app;
    }

}
