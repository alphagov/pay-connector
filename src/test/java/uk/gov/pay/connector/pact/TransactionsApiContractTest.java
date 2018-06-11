package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

@RunWith(PayPactRunner.class)
@Provider("connector")
@PactBroker(protocol = "https", host = "pact-broker-test.cloudapps.digital", port = "443", tags = {"${PACT_CONSUMER_TAG}"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"))
public class TransactionsApiContractTest {

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

    public static void setUpGatewayAccount() {
        dbHelper.addGatewayAccount("666", "sandbox");
        dbHelper.addCharge("acharge", "666", 123, ChargeStatus.CREATED, "https:example.com", "atransactionid");
    }
    
    @State({"User 666 exists in the database and has available transactions"})
    public void accountWithTransactions() {
        setUpGatewayAccount();
    }
}
