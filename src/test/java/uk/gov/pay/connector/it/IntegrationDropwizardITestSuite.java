package uk.gov.pay.connector.it;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import uk.gov.pay.connector.it.resources.CardAuthoriseResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.CardCaptureResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.CardTypesResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargeCancelFrontendResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargeCancelResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargeEventsResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargeExpiryResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargesApiResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.ChargesFrontendResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.DatabaseConnectionDropwizardITest;
import uk.gov.pay.connector.it.resources.EmailNotificationResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.GatewayAccountFrontendResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.GatewayAccountResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.HealthCheckResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.SearchChargesByDateResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.SecurityTokensResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.TransactionsApiResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.TransactionsSummaryResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqCardResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqChargeCancelResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqNotificationResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.epdq.EpdqRefundDropwizardITest;
import uk.gov.pay.connector.it.resources.sandbox.SandboxNotificationResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.sandbox.SandboxRefundDropwizardITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayCardResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayNotificationResourceWithAccountSpecificAuthDropwizardITest;
import uk.gov.pay.connector.it.resources.smartpay.SmartpayRefundDropwizardITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayCardResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayChargeCancelResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayNotificationResourceDropwizardITest;
import uk.gov.pay.connector.it.resources.worldpay.WorldpayRefundDropwizardITest;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresTemplateRule;
import uk.gov.pay.connector.util.PortFactory;

import static io.dropwizard.testing.ConfigOverride.config;

@RunWith(Suite.class)

@Suite.SuiteClasses(value = {
        EpdqCardResourceDropwizardITest.class,
        EpdqChargeCancelResourceDropwizardITest.class,
        EpdqNotificationResourceDropwizardITest.class,
        EpdqRefundDropwizardITest.class,
        SandboxNotificationResourceDropwizardITest.class,
        SandboxRefundDropwizardITest.class,
        SmartpayCardResourceDropwizardITest.class,
        SmartpayNotificationResourceWithAccountSpecificAuthDropwizardITest.class,
        SmartpayRefundDropwizardITest.class,
        WorldpayCardResourceDropwizardITest.class,
        WorldpayChargeCancelResourceDropwizardITest.class,
        WorldpayNotificationResourceDropwizardITest.class,
        WorldpayRefundDropwizardITest.class,
        CardAuthoriseResourceDropwizardITest.class,
        CardCaptureResourceDropwizardITest.class,
        CardTypesResourceDropwizardITest.class,
        ChargeCancelFrontendResourceDropwizardITest.class,
        ChargeCancelResourceDropwizardITest.class,
        ChargeEventsResourceDropwizardITest.class,
        ChargeExpiryResourceDropwizardITest.class,
        ChargesApiResourceDropwizardITest.class,
        ChargesFrontendResourceDropwizardITest.class,
        DatabaseConnectionDropwizardITest.class,
        EmailNotificationResourceDropwizardITest.class,
        GatewayAccountFrontendResourceDropwizardITest.class,
        GatewayAccountResourceDropwizardITest.class,
        HealthCheckResourceDropwizardITest.class,
        SearchChargesByDateResourceDropwizardITest.class,
        SecurityTokensResourceDropwizardITest.class,
        TransactionsApiResourceDropwizardITest.class,
        TransactionsSummaryResourceDropwizardITest.class
})
public class IntegrationDropwizardITestSuite {

    private static int port = PortFactory.findFreePort();

    @ClassRule
    public static DropwizardAppWithPostgresTemplateRule app = new DropwizardAppWithPostgresTemplateRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("epdq.urls.test", "http://localhost:" + port + "/epdq"));

    public static DropwizardAppWithPostgresTemplateRule getApp() {
        return app;
    }
}
