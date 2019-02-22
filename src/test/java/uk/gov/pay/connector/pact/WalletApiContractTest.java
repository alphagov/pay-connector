package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.IgnoreNoPactsToVerify;
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
import uk.gov.pay.connector.pact.util.GatewayAccountUtil;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RunWith(PactRunner.class)
@Provider("connector")
@IgnoreNoPactsToVerify()
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

    @State("a sandbox account exists with a charge with id testChargeId that is in state ENTERING_CARD_DETAILS.")
    public void aChangeExistsAwaitingAuthorisation(Map<String, String> params) {
        long gatewayAccountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, gatewayAccountId);

        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = "testChargeId";

        dbHelper.addCharge(chargeId, chargeExternalId, String.valueOf(gatewayAccountId), 100L, ChargeStatus.ENTERING_CARD_DETAILS, "aReturnUrl",
                chargeExternalId, ServicePaymentReference.of("aReference"), ZonedDateTime.now(), "test@test.com", false);
    }
}
