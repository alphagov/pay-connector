package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}", "test", "staging", "production"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"frontend"})
public class WalletApiContractTest {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @TestTarget
    public static Target target;
    private static DatabaseTestHelper dbHelper;

    @BeforeClass
    public static void setUp() {
        target = new HttpTarget(app.getLocalPort());
        dbHelper = app.getDatabaseTestHelper();
    }

    @Before
    public void refreshDatabase() {
        dbHelper.truncateAllData();
    }

    private void setUpGatewayAccount(long accountId) {
        if (dbHelper.getAccountCredentials(accountId) == null) {
            DatabaseFixtures
                    .withDatabaseTestHelper(dbHelper)
                    .aTestAccount()
                    .withAccountId(accountId)
                    .withPaymentProvider("sandbox")
                    .withDescription("aDescription")
                    .withAnalyticsId("8b02c7e542e74423aa9e6d0f0628fd58")
                    .withServiceName("a cool service")
                    .insert();
        } else {
            dbHelper.deleteAllChargesOnAccount(accountId);
        }
    }

    private void setUpCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus, ZonedDateTime createdDate, boolean delayedCapture) {
        dbHelper.addCharge(chargeId, chargeExternalId, accountId, 100L, chargeStatus, "aReturnUrl",
                chargeExternalId, ServicePaymentReference.of("aReference"), createdDate, "test@test.com", delayedCapture);
    }

    @State("a charge exists and is awaiting authorisation.")
    public void aChangeExistsAwaitingAuthorisation(Map<String, String> params) {
        Long gatewayAccountId = 666L;
        setUpGatewayAccount(gatewayAccountId);

        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = "testChargeId";

        setUpCharge(gatewayAccountId.toString(), chargeId, chargeExternalId, ChargeStatus.ENTERING_CARD_DETAILS, ZonedDateTime.now(), false);
    }
}
