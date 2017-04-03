package uk.gov.pay.connector.it.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;
import uk.gov.pay.connector.util.PortFactory;

import java.util.Map;

import static io.dropwizard.testing.ConfigOverride.config;
import static uk.gov.pay.connector.model.domain.GatewayAccount.*;

abstract public class CardCaptureProcessBaseITest {

    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password"
            );

    protected int port = PortFactory.findFreePort();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(port);

    @Rule
    public GuiceAppWithPostgresRule app = new GuiceAppWithPostgresRule(
            config("worldpay.urls.test", "http://localhost:" + port + "/jsp/merchant/xml/paymentService.jsp"),
            config("smartpay.urls.test", "http://localhost:" + port + "/pal/servlet/soap/Payment"),
            config("captureProcessConfig.maximumRetries", "1"),
            config("captureProcessConfig.retryFailuresEvery", "0 minutes"));

    public DatabaseFixtures.TestCharge createTestCharge(String paymentProvider, ChargeStatus chargeStatus) {
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withPaymentProvider(paymentProvider)
                .withCredentials(CREDENTIALS);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(chargeStatus)
                .withTransactionId(TRANSACTION_ID);

        testAccount.insert();
        return testCharge.insert();
    }
}