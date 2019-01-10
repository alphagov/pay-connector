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
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}", "test", "staging", "production"},
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

    private void setUpCharges(int numberOfCharges, String accountId, ZonedDateTime createdDate) {
        for (int i = 0; i < numberOfCharges; i++) {
            long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
            setUpSingleCharge(accountId, chargeId, Long.toString(chargeId), ChargeStatus.CREATED, createdDate, false);
        }
    }

    private void setUpSingleCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus, ZonedDateTime createdDate, boolean delayedCapture, String cardHolderName, String lastDigitsCardNumber, String firstDigitsCardNumber) {
        dbHelper.addCharge(chargeId, chargeExternalId, accountId, 100L, chargeStatus, "aReturnUrl",
                chargeExternalId, ServicePaymentReference.of("aReference"), createdDate, "test@test.com", delayedCapture);
        dbHelper.updateChargeCardDetails(chargeId, "visa", lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName, "08/23",
                "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
    }

    private void setUpSingleCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus, ZonedDateTime createdDate, boolean delayedCapture) {
        setUpSingleCharge(accountId, chargeId, chargeExternalId, chargeStatus, createdDate, delayedCapture, "aName", "0001", "123456");
    }

    private void setUpChargeAndRefunds(int numberOfRefunds, String accountID, ZonedDateTime createdDate) {
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        dbHelper.addCharge(chargeId, Long.toString(chargeId), accountID, 100L, ChargeStatus.CREATED, "aReturnUrl",
                "aTransactionId", ServicePaymentReference.of("aReference"), ZonedDateTime.now().minusHours(12), "test@test.com@");

        for (int i = 0; i < numberOfRefunds; i++) {
            long refundId = ThreadLocalRandom.current().nextLong(100, 100000);

            dbHelper.addRefund(refundId, String.valueOf(refundId), "reference", 1L, RefundStatus.REFUNDED.getValue(),
                    chargeId, createdDate);
        }
    }

    private void cancelCharge(String gatewayAccountId, String chargeExternalId) {
        given().port(app.getLocalPort()).post(format("/v1/api/accounts/%s/charges/%s/cancel", gatewayAccountId, chargeExternalId));
    }

    @State("a canceled charge exists")
    public void aCanceledChargeExists(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        setUpGatewayAccount(Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), false);
        cancelCharge(gatewayAccountId, chargeExternalId);
    }


    @State("User 666 exists in the database")
    public void account666Exists() {
        long accountId = 666L;
        setUpGatewayAccount(accountId);
    }

    @State("User 666 exists in the database and has 5 transactions available")
    public void account666WithTransactions() {
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(5, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring after 2018-05-03T00:00:00.000Z")
    public void accountWithTransactionsAfterMay2018() {
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring before 2018-05-03T00:00:01.000Z")
    public void accountWithTransactionsBeforeMay2018() {
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault()));
    }

    @State("User 666 exists in the database and has 2 available transactions between 2018-05-14T00:00:00 and 2018-05-15T00:00:00")
    public void accountWithTransactionsOnMay_5_2018() {
        long accountId = 666L;
        setUpGatewayAccount(accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018, 5, 14, 1, 1, 1, 1, ZoneId.systemDefault()));
    }

    @State({"default", "Card types exist in the database"})
    public void defaultCase() {
    }

    @State("a gateway account with external id exists")
    public void createGatewayAccount(Map<String, String> params) {
        dbHelper.addGatewayAccount(params.get("gateway_account_id"), "sandbox");
    }

    @State("a charge with delayed capture true exists")
    public void createChargeWithDelayedCaptureTrue(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        setUpGatewayAccount(Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), true);
    }

    @State("a charge with delayed capture true and awaiting capture request status exists")
    public void createChargeWithDelayedCaptureTrueAndAwaitingCaptureRequestStatus(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        setUpGatewayAccount(Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.AWAITING_CAPTURE_REQUEST, ZonedDateTime.now(), true);
    }

    @State("a charge with card details exists")
    public void createChargeWithCardDetails(Map<String, String> params) {
        String chargeExternalId = params.get("charge_id");
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String cardHolderName = params.get("cardholder_name");
        String lastDigitsCardNumber = params.get("last_digits_card_number");
        String firstDigitsCardNumber = params.get("first_digits_card_number");
        setUpGatewayAccount(Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.parse("2018-09-22T10:13:16.067Z"), true, cardHolderName, lastDigitsCardNumber, firstDigitsCardNumber);
    }

    @State("Refunds exist")
    public void refundsExist(Map<String, String> params) {
        long accountId = Long.parseLong(params.get("account_id"));
        ZonedDateTime createdDate = Optional.ofNullable(params.get("created_date"))
                .map(ZonedDateTime::parse)
                .orElse(ZonedDateTime.now());
        setUpGatewayAccount(accountId);
        setUpChargeAndRefunds(2, params.get("account_id"), createdDate);
    }

    @State("Account exists")
    public void accountExists(Map<String, String> params) {
        Long accountId = Long.valueOf(params.get("account_id"));
        setUpGatewayAccount(accountId);
        setUpCharges(1, params.get("account_id"), ZonedDateTime.now().minusHours(12));
    }

    @State("a charge with corporate surcharge exists")
    public void createChargeWithCorporateCardSurcharge(Map<String, String> params) {
        long accountId = Long.parseLong(params.get("account_id"));
        setUpGatewayAccount(accountId);
        String chargeExternalId = params.get("charge_id");
        dbHelper.addChargeWithCorporateCardSurcharge(1234L, chargeExternalId, Long.toString(accountId), 2000L,
                ChargeStatus.CAPTURED, "https://someurl.example", chargeExternalId, ServicePaymentReference.of("My reference"),
                ZonedDateTime.now(), SupportedLanguage.ENGLISH, false, 250L);
    }
}
