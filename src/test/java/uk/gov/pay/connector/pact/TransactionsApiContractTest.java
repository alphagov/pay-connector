package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.PactRunner;
import au.com.dius.pact.provider.junit.Provider;
import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.loader.PactBroker;
import au.com.dius.pact.provider.junit.loader.PactBrokerAuth;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.pact.util.GatewayAccountUtil;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;

@RunWith(PactRunner.class)
@Provider("connector")
@PactBroker(scheme = "https", host = "pact-broker-test.cloudapps.digital", tags = {"${PACT_CONSUMER_TAG}", "test", "staging", "production"},
        authentication = @PactBrokerAuth(username = "${PACT_BROKER_USERNAME}", password = "${PACT_BROKER_PASSWORD}"),
        consumers = {"selfservice", "publicapi"})
public class TransactionsApiContractTest {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @TestTarget
    public static Target target;
    private static DatabaseTestHelper dbHelper;
    private static ObjectMapper objectMapper = new ObjectMapper();

    @BeforeClass
    public static void setUp() {
        target = new HttpTarget(app.getLocalPort());
        dbHelper = app.getDatabaseTestHelper();

    }

    @Before
    public void refreshDatabase() {
        dbHelper.truncateAllData();
    }

    @State("a charge with metadata exists")
    public void aChargeWithMetadataExists(Map<String, String> params) throws Exception {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(gatewayAccountId)
                .withAmount(100)
                .withStatus(ChargeStatus.CAPTURED)
                .withReturnUrl("https://somewhere.gov.uk/rainbow/1")
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("aReference"))
                .withCreatedDate(ZonedDateTime.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .withExternalMetadata(new ExternalMetadata(objectMapper.readValue(params.get("metadata"), Map.class)))
                .build());
    }

    @State("a charge with a gateway transaction id exists")
    public void aChargeWithGatewayTxIdExists(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(gatewayAccountId)
                .withAmount(100)
                .withStatus(ChargeStatus.CAPTURED)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(params.get("gateway_transaction_id"))
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("aReference"))
                .withCreatedDate(ZonedDateTime.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .build());
    }

    private void setUpCharges(int numberOfCharges, String accountId, ZonedDateTime createdDate) {
        for (int i = 0; i < numberOfCharges; i++) {
            long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
            setUpSingleCharge(accountId, chargeId, Long.toString(chargeId), ChargeStatus.CREATED, createdDate, false);
        }
    }

    private void setUpSingleCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus,
                                   ZonedDateTime createdDate, boolean delayedCapture, String cardHolderName,
                                   String lastDigitsCardNumber, String firstDigitsCardNumber, String gatewayTransactionId) {
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(accountId)
                .withAmount(100)
                .withStatus(chargeStatus)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(gatewayTransactionId)
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("aReference"))
                .withCreatedDate(createdDate)
                .withEmail("test@test.com")
                .withDelayedCapture(delayedCapture)
                .build());
        dbHelper.updateChargeCardDetails(chargeId, "visa", lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName, "08/23",
                "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
    }

    private void setUpSingleCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus, ZonedDateTime createdDate, boolean delayedCapture) {
        setUpSingleCharge(accountId, chargeId, chargeExternalId, chargeStatus, createdDate, delayedCapture, "aName", "0001", "123456", "aGatewayTransactionId");
    }

    private void setUpChargeAndRefunds(int numberOfRefunds, String accountID, ZonedDateTime createdDate) {
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        dbHelper.addCharge(chargeId, Long.toString(chargeId), accountID, 100L, ChargeStatus.CREATED, "aReturnUrl",
                "aTransactionId", ServicePaymentReference.of("aReference"), ZonedDateTime.now().minusHours(12), "test@test.com@");

        for (int i = 0; i < numberOfRefunds; i++) {
            dbHelper.addRefund("external" + RandomUtils.nextInt(), "reference", 1L, REFUNDED,
                    chargeId, randomAlphanumeric(10), createdDate);
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
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), false);
        cancelCharge(gatewayAccountId, chargeExternalId);
    }


    @State("User 666 exists in the database")
    public void account666Exists() {
        long accountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
    }

    @State("User 666 exists in the database and has 5 transactions available")
    public void account666WithTransactions() {
        long accountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpCharges(5, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring after 2018-05-03T00:00:00.000Z")
    public void accountWithTransactionsAfterMay2018() {
        long accountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.now());
    }

    @State("User 666 exists in the database and has 2 available transactions occurring before 2018-05-03T00:00:01.000Z")
    public void accountWithTransactionsBeforeMay2018() {
        long accountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault()));
    }

    @State("User 666 exists in the database and has 2 available transactions between 2018-05-14T00:00:00 and 2018-05-15T00:00:00")
    public void accountWithTransactionsOnMay_5_2018() {
        long accountId = 666L;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpCharges(2, Long.toString(accountId), ZonedDateTime.of(2018, 5, 14, 1, 1, 1, 1, ZoneId.systemDefault()));
    }

    @State("a stripe gateway account with external id 42 exists in the database")
    public void stripeAccountExists() {
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(42L)
                .withPaymentProvider("stripe")
                .withCredentials(Collections.singletonMap("stripe_account_id", "acct_123example123"))
                .insert();
    }

    @State("a gateway account supporting digital wallet with external id 666 exists in the database")
    public void anAccountExists() {
        String aDigitalWalletSupportedPaymentProvider = "worldpay";
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(666L)
                .withPaymentProvider(aDigitalWalletSupportedPaymentProvider)
                .insert();
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
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), true);
    }

    @State("a charge with delayed capture true and awaiting capture request status exists")
    public void createChargeWithDelayedCaptureTrueAndAwaitingCaptureRequestStatus(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.AWAITING_CAPTURE_REQUEST, ZonedDateTime.now(), true);
    }

    @State("a charge in state CREATED with accountId 42 and chargeExternalId 100 and metadata exists")
    public void createChargeWithMetadata() {
        String gatewayAccountId = "42";
        String chargeExternalId = "100";
        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), false);

        var externalMetadata = new ExternalMetadata(
                Map.of("key1", "some string", "key2", true, "key3", 123));
        dbHelper.addExternalMetadata(chargeId, externalMetadata);
    }

    @State("a charge with card details exists")
    public void createChargeWithCardDetails(Map<String, String> params) {
        String chargeExternalId = params.get("charge_id");
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String cardHolderName = params.get("cardholder_name");
        String lastDigitsCardNumber = params.get("last_digits_card_number");
        String firstDigitsCardNumber = params.get("first_digits_card_number");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED,
                ZonedDateTime.parse("2018-09-22T10:13:16.067Z"), true, cardHolderName, lastDigitsCardNumber,
                firstDigitsCardNumber, params.get("gateway_transaction_id"));
    }

    @State("Refunds exist")
    public void refundsExist(Map<String, String> params) {
        long accountId = Long.parseLong(params.get("account_id"));
        ZonedDateTime createdDate = Optional.ofNullable(params.get("created_date"))
                .map(ZonedDateTime::parse)
                .orElse(ZonedDateTime.now());
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpChargeAndRefunds(2, params.get("account_id"), createdDate);
    }

    @State("Account exists")
    public void accountExists(Map<String, String> params) {
        Long accountId = Long.valueOf(params.get("account_id"));
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        setUpCharges(1, params.get("account_id"), ZonedDateTime.now().minusHours(12));
    }

    @State("a charge with corporate surcharge exists")
    public void createChargeWithCorporateCardSurcharge(Map<String, String> params) {
        long accountId = Long.parseLong(params.get("account_id"));
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        String chargeExternalId = params.get("charge_id");
        dbHelper.addChargeWithCorporateCardSurcharge(1234L, chargeExternalId, Long.toString(accountId), 2000L,
                ChargeStatus.CAPTURED, "https://someurl.example", chargeExternalId, ServicePaymentReference.of("My reference"),
                ZonedDateTime.now(), SupportedLanguage.ENGLISH, false, 250L);
    }
}
