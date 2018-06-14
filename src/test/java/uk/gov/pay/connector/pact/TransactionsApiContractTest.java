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

import java.time.ZoneId;
import java.time.ZonedDateTime;

@RunWith(PayPactRunner.class)
@Provider("connector")
@PactBroker(protocol = "https", host = "pact-broker-test.cloudapps.digital", port = "443", tags = {"${PACT_CONSUMER_TAG}"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"))
public class TransactionsApiContractTest {

    private long chargeIdCounter;

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

    private void setUpGatewayAccount(long accountId) {
        if (dbHelper.getAccountCredentials(accountId) == null) {
            dbHelper.addGatewayAccount(Long.toString(accountId), "sandbox", "aDescription", "8b02c7e542e74423aa9e6d0f0628fd58");
        } else {
            dbHelper.deleteAllChargesOnAccount(accountId);
        }
    }

    private void setUpCharges(int numberOfCharges, String accountID, ZonedDateTime createdDate){
        for (int i = 0; i < numberOfCharges; i++) {
            long chargeId = chargeIdCounter++;
            dbHelper.addCharge(chargeId, Long.toString(chargeId), accountID, 100L, ChargeStatus.CREATED,"aReturnUrl","aTransactionId", "aReference", createdDate, "test@test.com@");
            dbHelper.updateChargeCardDetails(chargeId, "visa", "0001", "aName", "08/23", "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
        }
    }

    @State("User 666 exists in the database")
    public void account666Exists(){
        long accountId = 666L;
        setUpGatewayAccount(accountId);
    }

    @State("User 666 exists in the database and has 4 transactions available")
    public void account666WithTransactions(){
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(4, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring after 2018-05-03T00:00:00.000Z")
    public void accountWithTransactionsAfterMay2018(){
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring before 2018-05-03T00:00:01.000Z")
    public void accountWithTransactionsBeforeMay2018(){
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018,1,1,1,1,1,1,ZoneId.systemDefault()));
    }

    @State("User 666 exists in the database and has 2 available transactions between 2018-05-14T00:00:00 and 2018-05-15T00:00:00")
    public void accountWithTransactionsOnMay_5_2018(){
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018,5,14,1,1,1,1,ZoneId.systemDefault()));
    }

    @State({"default", "Card types exist in the database"})
    public void defaultCase() { }
}
