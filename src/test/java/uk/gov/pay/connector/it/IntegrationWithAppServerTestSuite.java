package uk.gov.pay.connector.it;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.it.resources.CardAuthoriseResourceITest;
import uk.gov.pay.connector.it.resources.CardCaptureResourceITest;
import uk.gov.pay.connector.it.resources.CardTypesResourceITest;
import uk.gov.pay.connector.it.resources.ChargeCancelFrontendResourceITest;
import uk.gov.pay.connector.it.resources.ChargeCancelResourceITest;
import uk.gov.pay.connector.it.resources.ChargeEventsResourceITest;
import uk.gov.pay.connector.it.resources.ChargeExpiryResourceITest;
import uk.gov.pay.connector.it.resources.ChargesApiResourceITest;
import uk.gov.pay.connector.it.resources.ChargesFrontendResourceITest;
import uk.gov.pay.connector.it.resources.DatabaseConnectionITest;
import uk.gov.pay.connector.it.resources.EmailNotificationResourceITest;
import uk.gov.pay.connector.it.resources.GatewayAccountFrontendResourceITest;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceITest;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceTestBase;
import uk.gov.pay.connector.it.resources.HealthCheckResourceITest;
import uk.gov.pay.connector.it.resources.SearchChargesByDateResourceITest;
import uk.gov.pay.connector.it.resources.SecurityTokensResourceITest;
import uk.gov.pay.connector.it.resources.TransactionsApiResourceITest;
import uk.gov.pay.connector.it.resources.TransactionsSummaryResourceITest;
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
public class IntegrationWithAppServerTestSuite {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationWithAppServerTestSuite.class);

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
