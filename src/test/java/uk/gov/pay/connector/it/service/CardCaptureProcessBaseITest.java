package uk.gov.pay.connector.it.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.util.ChargeUtils;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

abstract public class CardCaptureProcessBaseITest {

    protected int CAPTURE_MAX_RETRIES = 1;
    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password",
                CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphraser",
                    "stripe_account_id", "stripe_account_id"
            );

    protected int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("stripe.url", "http://localhost:" + port ),
            config("epdq.urls.test", "http://localhost:" + port + "/epdq"),
            config("captureProcessConfig.maximumRetries", Integer.toString(CAPTURE_MAX_RETRIES)),
            config("captureProcessConfig.retryFailuresEvery", "0 minutes"));

    public DatabaseFixtures.TestCharge createTestCharge(String paymentProvider, ChargeStatus chargeStatus) {
        return ChargeUtils.createTestCharge(app.getDatabaseTestHelper(),
                paymentProvider, chargeStatus, CREDENTIALS, TRANSACTION_ID);
    }
}
