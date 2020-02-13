package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.dropwizard.testing.ConfigOverride;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.pact.util.GatewayAccountUtil;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.rules.SQSMockClient;
import uk.gov.pay.connector.util.AddChargeParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static uk.gov.pay.connector.cardtype.model.domain.CardType.DEBIT;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.rules.AppWithPostgresRule.WIREMOCK_PORT;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ContractTest {

    @ClassRule
    public static DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule(ConfigOverride.config("captureProcessConfig.backgroundProcessingEnabled", "false"));

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    private SQSMockClient sqsMockClient = new SQSMockClient();

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
                .withCardType(DEBIT)
                .withEmail("test@test.com")
                .withDelayedCapture(delayedCapture)
                .build());
        dbHelper.updateChargeCardDetails(chargeId, "visa", lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName, "08/23", String.valueOf(DEBIT),
                "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
    }

    private void setUpSingleCharge(String accountId, Long chargeId, String chargeExternalId, ChargeStatus chargeStatus, ZonedDateTime createdDate, boolean delayedCapture) {
        sqsMockClient.mockSuccessfulSendChargeToQueue(chargeExternalId);
        setUpSingleCharge(accountId, chargeId, chargeExternalId, chargeStatus, createdDate, delayedCapture, "aName", "0001", "123456", "aGatewayTransactionId");
    }

    private void setUpRefunds(int numberOfRefunds, Long chargeId,
                              ZonedDateTime createdDate, RefundStatus refundStatus, String chargeExternalId) {
        for (int i = 0; i < numberOfRefunds; i++) {
            dbHelper.addRefund("external" + RandomUtils.nextInt(), "reference", 1L, refundStatus,
                    randomAlphanumeric(10), createdDate, "user_external_id1234", null, chargeExternalId);
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

    @State("gateway accounts with ids 111, 222 exist in the database")
    public void multipleGatewayAccountsExist() {
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(111L)
                .insert();
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(222L)
                .insert();
    }
    
    @State({"default", "Card types exist in the database"})
    public void defaultCase() {
    }

    @State("a gateway account with external id exists")
    public void createGatewayAccount(Map<String, String> params) {
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(params.get("gateway_account_id"))
                .withPaymentGateway("sandbox")
                .withServiceName("a cool service")
                .build());
    }
    
    @State("a gateway account has moto payments enabled")
    public void createGatewayAccountWithMotoEnabled(Map<String, String> params) {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(toJson(Map.of("op", "replace","path", "allow_moto", "value", true)))
                .patch("/v1/api/accounts/" + params.get("gateway_account_id"))
                .then()
                .statusCode(OK.getStatusCode());
    }

    @State("a charge with fee and net_amount exists")
    public void createACharge(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CAPTURED, ZonedDateTime.now(), false);
        dbHelper.addFee(randomAlphanumeric(10), chargeId, 5, 5, ZonedDateTime.now(), randomAlphanumeric(10));
    }

    @State("a charge with delayed capture true exists")
    public void createChargeWithDelayedCaptureTrue(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), true);
    }

    @State("a charge exists")
    public void aChargeExists(Map<String, String> params) throws Exception {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(gatewayAccountId)
                .withAmount(100)
                .withCorporateSurcharge(250L)
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .withReturnUrl("aReturnUrl")
                .withTransactionId(params.get("gateway_transaction_id"))
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("aReference"))
                .withExternalMetadata(new ExternalMetadata(objectMapper.readValue("{\"ledger_code\":123, \"some_key\":\"key\"}", Map.class)))
                .withCreatedDate(ZonedDateTime.now())
                .withEmail("test@test.com")
                .withDelayedCapture(true)
                .build());
        dbHelper.addFee(randomAlphanumeric(10), chargeId, 5, 5, ZonedDateTime.now(), params.get("gateway_transaction_id"));
    }

    @State("a charge with delayed capture true and awaiting capture request status exists")
    public void createChargeWithDelayedCaptureTrueAndAwaitingCaptureRequestStatus(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.AWAITING_CAPTURE_REQUEST, ZonedDateTime.now(), true);
    }

    @State("Gateway account 42 exists and has a charge for £1 with id abc123")
    public void aChargeWithIdExists() {
        long accountId = 42;
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId("abc123")
                .withGatewayAccountId(String.valueOf(accountId))
                .withTransactionId("aGatewayTransactionId")
                .withAmount(100)
                .withStatus(ChargeStatus.CAPTURED)
                .build();
        dbHelper.addCharge(addChargeParams);
        dbHelper.updateChargeCardDetails(addChargeParams.getChargeId(),
                AuthCardDetailsFixture.anAuthCardDetails().build());
    }

    @State("Gateway account 42 exists and has a charge with id abc123 and has CREATED and AUTHORISATION_REJECTED charge events")
    public void aChargeWithIdAndChargeEvents() {
        String gatewayAccountId = "42";
        String chargeExternalId = "abc123";
        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        setUpSingleCharge(gatewayAccountId, chargeId, chargeExternalId, ChargeStatus.CREATED, ZonedDateTime.now(), false);

        dbHelper.addEvent(chargeId, ChargeStatus.CREATED.toString());
        dbHelper.addEvent(chargeId, ChargeStatus.AUTHORISATION_REJECTED.toString());
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

    @State("Refunds exist for a charge")
    public void refundsExistForACharge(Map<String, String> params) {
        String accountId = params.get("account_id");
        String chargeExternalId = params.get("charge_id");

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(accountId));
        long chargeId = 1234L;
        setUpSingleCharge(accountId, chargeId, chargeExternalId, ChargeStatus.CAPTURED, ZonedDateTime.now(), false);
        setUpRefunds(1, chargeId, ZonedDateTime.parse("2016-01-25T13:23:55Z"), REFUNDED, chargeExternalId);
        setUpRefunds(1, chargeId, ZonedDateTime.parse("2016-01-25T16:23:55Z"), REFUND_ERROR, chargeExternalId);
    }

    @State("a payment refund exists")
    public void paymentRefundExists(Map<String, String> params) {
        Long accountId = Long.parseLong(params.get("gateway_account_id"));
        Long paymentId = Long.parseLong(params.get("charge_id"));
        String refundId = params.get("refund_id");
        ZonedDateTime createdDate = Optional.ofNullable(params.get("created_date"))
                .map(ZonedDateTime::parse)
                .orElse(ZonedDateTime.now());

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(paymentId)
                .withExternalChargeId(Long.toString(paymentId))
                .withGatewayAccountId(accountId.toString())
                .withAmount(100)
                .withStatus(ChargeStatus.CREATED)
                .withReturnUrl("aReturnUrl")
                .withReference(ServicePaymentReference.of("aReference"))
                .withCreatedDate(ZonedDateTime.now().minusHours(12))
                .withTransactionId("aTransactionId")
                .withEmail("test@test.com")
                .build());
        dbHelper.addRefund(refundId, "reference", 100L, REFUNDED,
                randomAlphanumeric(10), createdDate,
                Long.toString(paymentId));
    }

    @State("a charge with corporate surcharge exists")
    public void createChargeWithCorporateCardSurcharge(Map<String, String> params) {
        long accountId = Long.parseLong(params.get("account_id"));
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, accountId);
        String chargeExternalId = params.get("charge_id");
        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(1234L)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(Long.toString(accountId))
                .withAmount(2000L)
                .withStatus(ChargeStatus.CAPTURED)
                .withReturnUrl("https://someurl.example")
                .withDescription("Test description")
                .withReference(ServicePaymentReference.of("My reference"))
                .withTransactionId(chargeExternalId)
                .withCorporateSurcharge(250L)
                .build());
    }
}
