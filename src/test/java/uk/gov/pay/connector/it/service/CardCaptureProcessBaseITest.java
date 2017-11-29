package uk.gov.pay.connector.it.service;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import uk.gov.pay.connector.it.IntegrationWithGuiceEmulatorTestSuite;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.GuiceAppWithPostgresRule;

import java.util.Map;

import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.model.domain.GatewayAccount.CREDENTIALS_USERNAME;

abstract public class CardCaptureProcessBaseITest {

    private static final String TRANSACTION_ID = "7914440428682669";
    private static final Map<String, String> CREDENTIALS =
            ImmutableMap.of(
                    CREDENTIALS_MERCHANT_ID, "merchant-id",
                    CREDENTIALS_USERNAME, "test-user",
                    CREDENTIALS_PASSWORD, "test-password",
                CREDENTIALS_SHA_IN_PASSPHRASE, "sha-passphraser"
            );


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(IntegrationWithGuiceEmulatorTestSuite.getExternalServicesPort());

   // @Rule
    public GuiceAppWithPostgresRule app = IntegrationWithGuiceEmulatorTestSuite.getApp();

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
