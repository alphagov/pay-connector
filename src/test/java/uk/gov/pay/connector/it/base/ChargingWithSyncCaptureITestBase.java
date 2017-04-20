package uk.gov.pay.connector.it.base;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Rule;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import static io.dropwizard.testing.ConfigOverride.config;

public class ChargingWithSyncCaptureITestBase extends ChargingITestCommon {

    private int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("asynchronousCapture", "false"));

    public ChargingWithSyncCaptureITestBase(String paymentProvider) {
        super(paymentProvider);
    }

    @Override
    public DropwizardAppWithPostgresRule getApplication() {
        return app;
    }
}
