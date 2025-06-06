package uk.gov.pay.connector.pact;

import au.com.dius.pact.provider.junit.State;
import au.com.dius.pact.provider.junit.target.HttpTarget;
import au.com.dius.pact.provider.junit.target.Target;
import au.com.dius.pact.provider.junit.target.TestTarget;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import io.dropwizard.testing.ConfigOverride;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.Exemption3dsType;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.client.cardid.model.CardInformation;
import uk.gov.pay.connector.client.cardid.model.CardidCardType;
import uk.gov.pay.connector.gateway.model.PayersCardPrepaidStatus;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.AuthCardDetailsFixture;
import uk.gov.pay.connector.pact.util.GatewayAccountUtil;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;
import uk.gov.pay.connector.paymentprocessor.model.Exemption3ds;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.AppWithPostgresAndSqsRule;
import uk.gov.pay.connector.rules.CardidStub;
import uk.gov.pay.connector.rules.LedgerStub;
import uk.gov.pay.connector.rules.StripeMockClient;
import uk.gov.pay.connector.rules.WorldpayMockClient;
import uk.gov.pay.connector.util.AddChargeParams;
import uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.service.payments.commons.model.AuthorisationMode;
import uk.gov.service.payments.commons.model.CardExpiryDate;
import uk.gov.service.payments.commons.model.charge.ExternalMetadata;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static uk.gov.pay.connector.cardtype.model.domain.CardType.DEBIT;
import static uk.gov.pay.connector.client.cardid.model.CardInformationFixture.aCardInformation;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_CODE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_STRIPE_ACCOUNT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.ONE_OFF_CUSTOMER_INITIATED;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.VERIFIED_WITH_LIVE_PAYMENT;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_ERROR;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.PAYMENT_CONFIRMED;
import static uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType.REFUND_ISSUED;
import static uk.gov.pay.connector.util.AddAgreementParams.AddAgreementParamsBuilder.anAddAgreementParams;
import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.AddGatewayAccountCredentialsParams.AddGatewayAccountCredentialsParamsBuilder.anAddGatewayAccountCredentialsParams;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;
import static uk.gov.pay.connector.util.AddPaymentInstrumentParams.AddPaymentInstrumentParamsBuilder.anAddPaymentInstrumentParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ContractTest {

    @ClassRule
    public static AppWithPostgresAndSqsRule app = new AppWithPostgresAndSqsRule(
            ConfigOverride.config("captureProcessConfig.backgroundProcessingEnabled", "false"));

    @TestTarget
    public static Target target;
    private static DatabaseTestHelper dbHelper;
    private static WorldpayMockClient worldpayMockClient;
    private static CardidStub mockCardidService;
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static LedgerStub ledgerStub;
    private static StripeMockClient stripeMockClient;

    @BeforeClass
    public static void setUp() {
        target = new HttpTarget(app.getLocalPort());
        dbHelper = app.getDatabaseTestHelper();
        worldpayMockClient = new WorldpayMockClient(app.getWireMockServer());
        mockCardidService = new CardidStub(app.getWireMockServer());
        ledgerStub = new LedgerStub(app.getWireMockServer());
        stripeMockClient = new StripeMockClient(app.getWireMockServer());
        Stripe.overrideApiBase("http://localhost:" + app.getWireMockPort());
    }

    @Before
    public void refreshDatabase() {
        dbHelper.truncateAllData();
    }

    @State("a sandbox gateway account with service id a-service-id exists and stripe is configured to create a connect account with id acct_123")
    public void sandbox_gateway_account_exists_stripe_is_configured() throws Exception {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(Map.of(
                        "service_id", "a-service-id",
                        "type", "test",
                        "payment_provider", "sandbox",
                        "service_name", "a-service-name"))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201);

        stripeMockClient.mockCreateStripeTestConnectAccount(); // acct_123 is set statically in this mocked response
        stripeMockClient.mockCreateExternalAccount();
        stripeMockClient.mockRetrieveAccount();
        stripeMockClient.mockRetrievePersonCollection();
        stripeMockClient.mockCreateRepresentativeForConnectAccount();
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
                .withCreatedDate(Instant.now())
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
                .withCreatedDate(Instant.now())
                .withEmail("test@test.com")
                .withDelayedCapture(false)
                .build());
    }

    public void setUpCharge(AddChargeParams params) {
        dbHelper.addCharge(params);
    }

    private void setUpRefunds(int numberOfRefunds, Long chargeId,
                              ZonedDateTime createdDate, RefundStatus refundStatus, String chargeExternalId) {
        for (int i = 0; i < numberOfRefunds; i++) {
            dbHelper.addRefund("external" + RandomUtils.nextInt(), 1L, refundStatus,
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
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(false)
                .withTransactionId(params.get("gateway_transaction_id"))
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
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

    @State("a gateway account with MOTO enabled and an external id 667 exists in the database")
    public void anAccountExistsWithMotoEnableAndMaskCardNumberAndSecurityCode() {
        DatabaseFixtures
                .withDatabaseTestHelper(dbHelper)
                .aTestAccount()
                .withAccountId(667L)
                .withAllowMoto(true)
                .withMotoMaskCardNumberInput(true)
                .withMotoMaskCardSecurityCodeInput(true)
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
    public void createGatewayAccount(Map<String, String> params) throws JsonProcessingException {
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(params.get("gateway_account_id"))
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("a cool service")
                .build());

        CardInformation cardInformation = aCardInformation().build();
        mockCardidService.returnCardInformation("4242424242424242", cardInformation);
    }

    @State("a gateway account with external id and recurring payment enabled exists")
    public void createGatewayAccountWithRecurringPaymentEnabled(Map<String, String> params) {
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(params.get("gateway_account_id"))
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("a cool service")
                .withRecurringEnabled(true)
                .build());
        ledgerStub.acceptPostEvent();
    }

    @State("the gateway account is disabled")
    public void disableGatewayAccount(Map<String, String> params) {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(toJson(Map.of("op", "replace", "path", "disabled", "value", true)))
                .patch("/v1/api/accounts/" + params.get("gateway_account_id"))
                .then()
                .statusCode(OK.getStatusCode());
    }

    @State("a gateway account has authorisation_api enabled")
    public void enableAuthorisationApiForGatewayAccount(Map<String, String> params) {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(toJson(Map.of("op", "replace", "path", "allow_authorisation_api", "value", true)))
                .patch("/v1/api/accounts/" + params.get("gateway_account_id"))
                .then()
                .statusCode(OK.getStatusCode());
    }

    @State("a gateway account has moto payments enabled")
    public void enableMotoForGatewayAccount(Map<String, String> params) {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(toJson(Map.of("op", "replace", "path", "allow_moto", "value", true)))
                .patch("/v1/api/accounts/" + params.get("gateway_account_id"))
                .then()
                .statusCode(OK.getStatusCode());
    }

    @State("a gateway account has telephone payment notifications enabled")
    public void enableTelephonePaymentNotificationsForGatewayAccount(Map<String, String> params) {
        given().port(app.getLocalPort())
                .contentType(JSON)
                .body(toJson(Map.of("op", "replace", "path", "allow_telephone_payment_notifications", "value", true)))
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
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withAmount(100)
                .withReference(ServicePaymentReference.of("aReference"))
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CAPTURED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(false)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withTransactionId("aGatewayTransactionId")
                .build();
        setUpCharge(addChargeParams);
        dbHelper.addFee(randomAlphanumeric(10), chargeId, 5, 5, ZonedDateTime.now(), randomAlphanumeric(10), FeeType.TRANSACTION);
    }

    @State("a charge with service external id 'a-service-id', account type 'test' and charge external id 'a-charge-id-1a2b3c' exists")
    public void createChargeWithServiceExternalIdAndAccountType() {
        long gatewayAccountId = new Random().nextLong(1000);
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, gatewayAccountId, "a-service-id");
        setUpCharge(anAddChargeParams()
                .withServiceId("a-service-id")
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withExternalChargeId("a-charge-id-1a2b3c")
                .build());
    }
    
    @State("a charge with gateway account id 42 and charge id abcdef1234 exists")
    public void createChargeWithHardCodedParams() {
        String gatewayAccountId = "42";
        String chargeExternalId = "abcdef1234";
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CAPTURED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(false)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
    }

    @State("a charge with delayed capture true exists")
    public void createChargeWithDelayedCaptureTrue(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withAmount(100)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(true)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
    }

    @State("a charge with authorisation mode moto_api exists")
    public void createChargeWithAuthorisationModeMotoApi(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(true)
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build();
        setUpCharge(addChargeParams);
    }

    @State("a charge with wallet type APPLE_PAY exists")
    public void createChargeWithWalletTypeApplePay(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withAmount(100)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CAPTURE_READY)
                .withCreatedDate(Instant.now())
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
        dbHelper.updateChargeCardDetails(chargeId, "visa", "0001", "123456", "aName",
                CardExpiryDate.valueOf("08/23"), String.valueOf(DEBIT),
                "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
        dbHelper.addWalletType(chargeId, WalletType.APPLE_PAY);
    }

    @State("a charge with authorisation mode moto_api and one_time_token exists")
    public void createChargeWithAuthorisationModeMotoApiAndOneTimeToken(Map<String, String> params) throws JsonProcessingException {
        CardInformation cardInformation = aCardInformation()
                .withPrepaidStatus(PayersCardPrepaidStatus.NOT_PREPAID)
                .withType(CardidCardType.DEBIT)
                .withBrand("visa")
                .build();
        mockCardidService.returnCardInformation("4242424242424242", cardInformation);

        String gatewayAccountId = params.get("gateway_account_id");
        String oneTimeToken = params.get("one_time_token");
        String oneTimeTokenUsed = params.get("one_time_token_used");
        String chargeExternalId = params.get("charge_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(true)
                .withAuthorisationMode(AuthorisationMode.MOTO_API)
                .build();
        setUpCharge(addChargeParams);
        dbHelper.addToken(chargeId, oneTimeToken, Boolean.valueOf(oneTimeTokenUsed));
    }

    @State("a charge exists")
    public void aChargeExists(Map<String, String> params) throws Exception {
        String gatewayAccountId = params.get("gateway_account_id");
        if (gatewayAccountId == null) {
            gatewayAccountId = "123456";
        }
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        if (chargeExternalId == null) {
            chargeExternalId = "ch_123abc456def";
        }
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
                .withCreatedDate(Instant.now())
                .withEmail("test@test.com")
                .withDelayedCapture(true)
                .build());
        dbHelper.addFee(randomAlphanumeric(10), chargeId, 5, 5, ZonedDateTime.now(), params.get("gateway_transaction_id"), FeeType.TRANSACTION);
        dbHelper.updateCharge3dsDetails(chargeId, null, null, null, "2.1.0");
    }

    @State("a charge with delayed capture true and awaiting capture request status exists")
    public void createChargeWithDelayedCaptureTrueAndAwaitingCaptureRequestStatus(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.AWAITING_CAPTURE_REQUEST)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(true)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
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
        dbHelper.updateChargeCardDetails(addChargeParams.chargeId(),
                AuthCardDetailsFixture.anAuthCardDetails().build());
    }

    @State("Gateway account 42 exists and has a charge with id abc123 and has CREATED and AUTHORISATION_REJECTED charge events")
    public void aChargeWithIdAndChargeEvents() {
        String gatewayAccountId = "42";
        String chargeExternalId = "abc123";
        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.now())
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);

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
        AddChargeParams addChargeParams = anAddChargeParams().withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CREATED)
                .withCreatedDate(Instant.parse("2018-09-22T10:13:16.067Z"))
                .withDelayedCapture(true)
                .withTransactionId(params.get("gateway_transaction_id"))
                .withAuthorisationMode(AuthorisationMode.WEB).build();
        setUpCharge(addChargeParams);
        dbHelper.updateChargeCardDetails(chargeId, "visa", lastDigitsCardNumber, firstDigitsCardNumber, cardHolderName,
                CardExpiryDate.valueOf("08/23"), String.valueOf(DEBIT),
                "aFirstAddress", "aSecondLine", "aPostCode", "aCity", "aCounty", "aCountry");
    }

    @State("Refunds exist for a charge")
    public void refundsExistForACharge(Map<String, String> params) {
        String accountId = params.get("account_id");
        String chargeExternalId = params.get("charge_id");

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(accountId));
        long chargeId = 1234L;

        AddChargeParams addChargeParams = anAddChargeParams()
                .withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(accountId)
                .withStatus(ChargeStatus.CAPTURED)
                .withCreatedDate(Instant.now())
                .withDelayedCapture(false)
                .withAuthorisationMode(AuthorisationMode.WEB)
                .build();
        setUpCharge(addChargeParams);
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
                .withCreatedDate(Instant.now().minus(Duration.ofHours(12)))
                .withTransactionId("aTransactionId")
                .withEmail("test@test.com")
                .build());

        dbHelper.addRefund(refundId, 100L, REFUNDED,
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

    @State("a gateway account 333 with Worldpay 3DS Flex credentials exists")
    public void aGatewayAccountWithWorldpay3dsFlexCredentialsExists() {
        final String worldpayGatewayAccountId = "333";
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("333")
                .withExternalId("abc123")
                .withPaymentGateway(WORLDPAY.getName())
                .withAnalyticsId("an-analytics-id")
                .withWorldpayCredentials()
                .build());
        dbHelper.insertWorldpay3dsFlexCredential(
                Long.valueOf(worldpayGatewayAccountId),
                "fa2daee2-1fbb-45ff-4444-52805d5cd9e0",
                "5bd9e0e4444dce153428c940", // pragma: allowlist secret
                "5bd9b55e4444761ac0af1c80", // pragma: allowlist secret
                1L);
        dbHelper.addEmailNotification(333L, "a template", true, PAYMENT_CONFIRMED);
        dbHelper.addEmailNotification(333L, null, true, REFUND_ISSUED);
    }

    @State("an ePDQ gateway account with id 333 and external abc123 with credentials exists")
    public void anEpdqGatewayAccountWithCredentialsExists() {
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("333")
                .withExternalId("abc123")
                .withPaymentGateway(EPDQ.getName())
                .withAnalyticsId("an-analytics-id")
                .withEpdqCredentials()
                .build());
        dbHelper.addEmailNotification(333L, "a template", true, PAYMENT_CONFIRMED);
        dbHelper.addEmailNotification(333L, null, true, REFUND_ISSUED);
    }

    @State("a Worldpay gateway account with id 333 exists and stub for validating credentials is set up")
    public void aWorldpayGatewayAccountWithExistsWithStubForValidatingSetup() {
        worldpayMockClient.mockCredentialsValidationValid();
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("333")
                .withWorldpayCredentials()
                .withPaymentGateway(WORLDPAY.getName())
                .build());
    }

    @State("a Worldpay gateway account with id 333 with gateway account credentials with id 444")
    public void aWorldpayGatewayAccountWithCredentialsWithIdExists() {
        AddGatewayAccountCredentialsParams gatewayAccountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withId(444)
                .withExternalId("an-external-id")
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.CREATED)
                .withGatewayAccountId(333)
                .build();
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("333")
                .withGatewayAccountCredentials(singletonList(gatewayAccountCredentialsParams))
                .withPaymentGateway(WORLDPAY.getName())
                .build());
    }

    @State("a Worldpay gateway account with id 3456, gateway account credentials with external_id creds123 exists")
    public void aWorldpayGatewayAccountAndCredentialsWithIdAndExternalIdExists() {
        AddGatewayAccountCredentialsParams gatewayAccountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withId(1234)
                .withExternalId("creds123")
                .withPaymentProvider("worldpay")
                .withState(ACTIVE)
                .withGatewayAccountId(3456)
                .build();
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("3456")
                .withGatewayAccountCredentials(singletonList(gatewayAccountCredentialsParams))
                .withPaymentGateway(WORLDPAY.getName())
                .build());
    }

    @State("a Worldpay gateway account with id 444 with gateway account credentials with id 555 and valid credentials")
    public void aWorldpayGatewayAccountWithFilledCredentialsWithIdExists() {
        Map<String, Object> credentials = Map.of(
                ONE_OFF_CUSTOMER_INITIATED, Map.of(
                        CREDENTIALS_MERCHANT_CODE, "a-merchant-code",
                        CREDENTIALS_USERNAME, "a-username",
                        CREDENTIALS_PASSWORD, "blablabla"));

        AddGatewayAccountCredentialsParams gatewayAccountCredentialsParams = anAddGatewayAccountCredentialsParams()
                .withId(555)
                .withExternalId("an-external-id")
                .withPaymentProvider("worldpay")
                .withState(GatewayAccountCredentialState.CREATED)
                .withGatewayAccountId(444)
                .withCredentials(credentials)
                .build();
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("444")
                .withGatewayAccountCredentials(singletonList(gatewayAccountCredentialsParams))
                .withPaymentGateway(WORLDPAY.getName())
                .build());
    }

    @State("a Worldpay gateway account with id 444 with two credentials ready to be switched")
    public void anAccountWithTwoCredentialsReadyForSwitchPsp() {
        long gatewayAccountId = 444L;
        String activeCredentialExternalId = "555aaa000";
        String switchingCredentialExternalId = "switchto1234";
        dbHelper.addGatewayAccount(
                anAddGatewayAccountParams()
                        .withAccountId(Long.toString(gatewayAccountId))
                        .withServiceName("a cool service")
                        .withProviderSwitchEnabled(true)
                        .withGatewayAccountCredentials(List.of(
                                anAddGatewayAccountCredentialsParams()
                                        .withGatewayAccountId(gatewayAccountId)
                                        .withCredentials(Map.of(
                                                ONE_OFF_CUSTOMER_INITIATED, Map.of(
                                                        CREDENTIALS_MERCHANT_CODE, "merchant-code",
                                                        CREDENTIALS_USERNAME, "username",
                                                        CREDENTIALS_PASSWORD, "password")))
                                        .withExternalId(activeCredentialExternalId)
                                        .withState(ACTIVE)
                                        .withPaymentProvider(WORLDPAY.getName())
                                        .build(),
                                anAddGatewayAccountCredentialsParams()
                                        .withGatewayAccountId(gatewayAccountId)
                                        .withCredentials(Map.of(
                                                CREDENTIALS_STRIPE_ACCOUNT_ID, "acct_0000000000000000"
                                        ))
                                        .withExternalId(switchingCredentialExternalId)
                                        .withState(VERIFIED_WITH_LIVE_PAYMENT)
                                        .withPaymentProvider(STRIPE.getName())
                                        .build()
                                ))
                        .build());
    }

    @State("an active agreement exists")
    public void anActiveAgreementExists(Map<String, String> params) {
        var agreementExternalId = params.get("agreement_external_id");
        var gatewayAccountId = params.get("gateway_account_id");
        var addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(nextLong())
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        dbHelper.addPaymentInstrument(addPaymentInstrumentParams);
        var agreementParams = anAddAgreementParams()
                .withGatewayAccountId(String.valueOf(gatewayAccountId))
                .withExternalAgreementId(agreementExternalId)
                .withPaymentInstrumentId(addPaymentInstrumentParams.getPaymentInstrumentId())
                .build();
        dbHelper.addAgreement(agreementParams);
    }

    @State("a charge created with an idempotency key for an agreement exists")
    public void aChargeCreatedWithAnIdempotencyKeyForAnAgreementExists(Map<String, String> params) {
        Instant createdDate = Instant.parse(params.get("created"));
        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_external_id");
        String idempotencyKey = params.get("idempotency_key");
        String agreementExternalId = params.get("agreement_external_id");
        long amount = Long.parseLong(params.get("amount"));
        String reference = params.get("reference");
        String description = params.get("description");
        String gatewayAccountId = dbHelper.getAgreementByExternalId(agreementExternalId).get("gateway_account_id").toString();

        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(gatewayAccountId)
                .withAgreementExternalId(agreementExternalId)
                .withAmount(amount)
                .withReference(ServicePaymentReference.of(reference))
                .withDescription(description)
                .withStatus(ChargeStatus.CREATED)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCreatedDate(createdDate)
                .build());

        dbHelper.insertIdempotency(idempotencyKey, Long.valueOf(gatewayAccountId), chargeExternalId, Map.of(
                "amount", amount,
                "reference", reference,
                "description", description,
                "agreement_id", agreementExternalId,
                "authorisation_mode", "agreement"));
    }

    @State("a charge with authorisation mode agreement and rejected status exists")
    public void aChargeWithAuthorisationModeAgreementAndRejectedStatusExists(Map<String, String> params) {
        long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_external_id");
        String agreementExternalId = params.get("agreement_external_id");
        long amount = Long.parseLong(params.get("amount"));
        String gatewayAccountId = dbHelper.getAgreementByExternalId(agreementExternalId).get("gateway_account_id").toString();

        dbHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(chargeExternalId)
                .withGatewayAccountId(gatewayAccountId)
                .withAgreementExternalId(agreementExternalId)
                .withAmount(amount)
                .withReference(ServicePaymentReference.of("reference"))
                .withDescription("description")
                .withStatus(ChargeStatus.AUTHORISATION_REJECTED)
                .withAuthorisationMode(AuthorisationMode.AGREEMENT)
                .withCanRetry(true)
                .build());
    }

    @State("a gateway account with id 3456 and an active agreement exists")
    public void aGatewayAccountWithId3456AndAnActiveAgreementExists() {
        ledgerStub.acceptPostEvent();
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId("3456")
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("a brilliant service")
                .withRecurringEnabled(true)
                .build());

        var addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(nextLong())
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        dbHelper.addPaymentInstrument(addPaymentInstrumentParams);

        var agreementParams = anAddAgreementParams()
                .withGatewayAccountId("3456")
                .withExternalAgreementId("abcdefghijklmnopqrstuvwxyz")
                .withPaymentInstrumentId(addPaymentInstrumentParams.getPaymentInstrumentId())
                .build();
        dbHelper.addAgreement(agreementParams);
    }

    @State("a gateway account and an active agreement exists")
    public void aGatewayAccountWithAnActiveAgreementExists(Map<String, String> params) {
        ledgerStub.acceptPostEvent();
        var agreementExternalId = Optional.ofNullable(params.get("agreement_external_id")).orElse("abcdefghijklmnopqrstuvwxyz");
        var gatewayAccountId = Optional.ofNullable(params.get("gateway_account_id")).orElse("3456");
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("rcp service")
                .withRecurringEnabled(true)
                .build());
        var addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(nextLong())
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        dbHelper.addPaymentInstrument(addPaymentInstrumentParams);
        var agreementParams = anAddAgreementParams()
                .withGatewayAccountId(gatewayAccountId)
                .withExternalAgreementId(agreementExternalId)
                .withPaymentInstrumentId(addPaymentInstrumentParams.getPaymentInstrumentId())
                .build();
        dbHelper.addAgreement(agreementParams);
    }

    @State("a gateway account and an agreement with cancelled status exists")
    public void aGatewayAccountWithACancelledStatusAgreementExists(Map<String, String> params) {
        ledgerStub.acceptPostEvent();
        var agreementExternalId = Optional.ofNullable(params.get("agreement_external_id")).orElse("abcdefghijklmnopqrstuvwxyz");
        var gatewayAccountId = Optional.ofNullable(params.get("gateway_account_id")).orElse("3456");
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("rcp service")
                .withRecurringEnabled(true)
                .build());
        var addPaymentInstrumentParams = anAddPaymentInstrumentParams()
                .withPaymentInstrumentId(nextLong())
                .withPaymentInstrumentStatus(PaymentInstrumentStatus.CANCELLED)
                .build();
        dbHelper.addPaymentInstrument(addPaymentInstrumentParams);
        var agreementParams = anAddAgreementParams()
                .withGatewayAccountId(gatewayAccountId)
                .withExternalAgreementId(agreementExternalId)
                .withPaymentInstrumentId(addPaymentInstrumentParams.getPaymentInstrumentId())
                .build();
        dbHelper.addAgreement(agreementParams);
    }

    @State("a gateway account and a new agreement exists")
    public void aGatewayAccountAndANewAgreementExists(Map<String, String> params) {
        ledgerStub.acceptPostEvent();

        var agreementExternalId = Optional.ofNullable(params.get("agreement_external_id")).orElse("abcdefghijklmnopqrstuvwxyz");
        var gatewayAccountId = Optional.ofNullable(params.get("gateway_account_id")).orElse("3456");
        dbHelper.addGatewayAccount(anAddGatewayAccountParams()
                .withAccountId(gatewayAccountId)
                .withPaymentGateway("sandbox")
                .withCredentials(Map.of())
                .withServiceName("rcp service")
                .withRecurringEnabled(true)
                .build());
        dbHelper.addAgreement(anAddAgreementParams()
                .withGatewayAccountId(gatewayAccountId)
                .withExternalAgreementId(agreementExternalId)
                .build());
    }

    @State("a charge with honoured corporate exemption exists")
    public void createAChargeWithHonouredExemption(Map<String, String> params) {
        String gatewayAccountId = params.get("gateway_account_id");
        if (gatewayAccountId == null) {
            gatewayAccountId = "123456";
        }
        Long chargeId = ThreadLocalRandom.current().nextLong(100, 100000);
        String chargeExternalId = params.get("charge_id");
        if (chargeExternalId == null) {
            chargeExternalId = "ch_123abc456def";
        }

        GatewayAccountUtil.setUpGatewayAccount(dbHelper, Long.valueOf(gatewayAccountId));
        AddChargeParams addChargeParams = anAddChargeParams().withExternalChargeId(chargeExternalId)
                .withChargeId(chargeId)
                .withGatewayAccountId(gatewayAccountId)
                .withStatus(ChargeStatus.CAPTURED)
                .withReference(ServicePaymentReference.of("aReference"))
                .withCreatedDate(Instant.now())
                .withAuthorisationMode(AuthorisationMode.WEB)
                .withExemption3ds(Exemption3ds.EXEMPTION_HONOURED)
                .withExemption3dsType(Exemption3dsType.CORPORATE)
                .withAmount(100)
                .withTransactionId("aGatewayTransactionId")
                .build();
        setUpCharge(addChargeParams);
        dbHelper.addFee(randomAlphanumeric(10), chargeId, 5, 5, ZonedDateTime.now(), randomAlphanumeric(10), FeeType.TRANSACTION);
    }
}
