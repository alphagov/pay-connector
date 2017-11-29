package uk.gov.pay.connector.it;

import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import static io.dropwizard.testing.ConfigOverride.config;

@RunWith(Suite.class)

@Suite.SuiteClasses(value = {
        uk.gov.pay.connector.it.service.epdq.CardCaptureProcessITest.class,
        uk.gov.pay.connector.it.service.sandbox.CardCaptureProcessITest.class,
        uk.gov.pay.connector.it.service.worldpay.CardCaptureProcessITest.class,
        uk.gov.pay.connector.it.service.smartpay.CardCaptureProcessITest.class,
        uk.gov.pay.connector.it.gatewayclient.GatewayCaptureFailuresITest.class,
        uk.gov.pay.connector.it.gatewayclient.GatewaySocketErrorITest.class,
        uk.gov.pay.connector.it.gatewayclient.GatewaySocketReadTimeoutITest.class
        })
public class IntegrationWithGuiceEmulatorTestSuite {

    private static final int externalServicesPort = PortFactory.findFreePort();
    public static final int CAPTURE_MAX_RETRIES = 1;

    @ClassRule
    public static GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + externalServicesPort + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + externalServicesPort + "/pal/servlet/soap/Payment"),
            config("epdq.urls.test", "http://localhost:" + externalServicesPort + "/epdq"),
            config("captureProcessConfig.maximumRetries", Integer.toString(CAPTURE_MAX_RETRIES)),
            config("captureProcessConfig.retryFailuresEvery", "0 minutes"));

    public static GuiceAppWithPostgresRule getApp() {
        return app;
    }

    public static int getExternalServicesPort() {
        return externalServicesPort;
    }
}
