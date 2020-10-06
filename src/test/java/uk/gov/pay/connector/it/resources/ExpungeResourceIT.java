package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.expunge.service.LedgerStub;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static java.time.ZonedDateTime.parse;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.pay.commons.model.SupportedLanguage.ENGLISH;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.events.EmittedEventFixture.anEmittedEventEntity;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userEmail;
import static uk.gov.pay.connector.model.domain.RefundEntityFixture.userExternalId;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ExpungeResourceIT {

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);

    @DropwizardTestContext
    protected TestContext testContext;

    EmittedEventDao emittedEventDao;

    private DatabaseFixtures.TestCharge expungeableCharge1;
    private DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures.TestAccount defaultTestAccount;

    private LedgerStub ledgerStub;
    private RefundDao refundDao;

    @Before
    public void setUp() {
        emittedEventDao = testContext.getInstanceFromGuiceContainer(EmittedEventDao.class);
        refundDao = testContext.getInstanceFromGuiceContainer(RefundDao.class);
        databaseTestHelper = testContext.getDatabaseTestHelper();
        insertTestAccount();
    }

    private void insertTestAccount() {
        this.defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(nextLong())
                .insert();
    }

    @Test
    public void shouldExpungeCharge() throws JsonProcessingException {
        var chargedId = ThreadLocalRandom.current().nextLong();
        ledgerStub = new LedgerStub();
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC).minusDays(91))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(expungeableCharge1);
        ledgerStub.returnLedgerTransaction("external_charge_id", expungeableCharge1, null);
        var charge = databaseTestHelper.containsChargeWithExternalId("external_charge_id");
        assertThat(charge, is(true));
        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);
        var postCharge = databaseTestHelper.containsChargeWithExternalId("external_charge_id");
        assertThat(postCharge, is(false));
    }

    private void insertChargeEvent(DatabaseFixtures.TestCharge charge) {
        DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper).aTestChargeEvent()
                .withChargeStatus(CREATED)
                .withDate(charge.getCreatedDate())
                .withChargeId(charge.getChargeId())
                .withTestCharge(charge)
                .insert();
    }

    @Test
    public void shouldUpdateTheParityCheckedDateOfNonCriteriaMatchedCharge() throws JsonProcessingException {
        var chargedId = ThreadLocalRandom.current().nextLong();
        ledgerStub = new LedgerStub();
        var date = parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC).minusDays(91)));
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId)
                .withCreatedDate(date)
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id")
                .withAmount(2500)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(expungeableCharge1);
        ledgerStub.returnLedgerTransactionWithMismatch("external_charge_id", expungeableCharge1, null);
        var charge = databaseTestHelper.containsChargeWithExternalId("external_charge_id");
        assertThat(charge, is(true));
        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);
        var postCharge = databaseTestHelper.getChargeByExternalId("external_charge_id");
        assertThat(postCharge.get("parity_check_date"), is(not(nullValue())));
    }

    @Test
    public void shouldNotExpungeChargeThatIsNotOldEnoughToBeExpunged() throws JsonProcessingException {
        var chargedId = ThreadLocalRandom.current().nextLong();
        ledgerStub = new LedgerStub();
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC)
                        .minusDays(89))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id_2")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(expungeableCharge1);
        ledgerStub.returnLedgerTransaction("external_charge_id", expungeableCharge1, null);
        var charge = databaseTestHelper.containsChargeWithExternalId("external_charge_id_2");
        assertThat(charge, is(true));
        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);
        var postCharge = databaseTestHelper.containsChargeWithExternalId("external_charge_id_2");
        assertThat(postCharge, is(true));
    }

    @Test
    public void shouldExpungeChargesMeetingCriteriaButNotThoseThatDont() throws JsonProcessingException {
        var chargedId = ThreadLocalRandom.current().nextLong();
        ledgerStub = new LedgerStub();

        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC)
                        .minusDays(89))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id_10")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(expungeableCharge1);
        ledgerStub.returnLedgerTransaction("external_charge_id_10", expungeableCharge1, null);

        var chargedId2 = ThreadLocalRandom.current().nextLong();

        var nonExpungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId2)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC)
                        .minusDays(91))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id_11")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(nonExpungeableCharge1);
        ledgerStub.returnLedgerTransaction("external_charge_id_11", nonExpungeableCharge1, null);

        var chargedId3 = ThreadLocalRandom.current().nextLong();

        var expungeableCharge2 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId3)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC)
                        .minusDays(88))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id_12")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(expungeableCharge2);
        ledgerStub.returnLedgerTransaction("external_charge_id_12", expungeableCharge2, null);

        var chargedId4 = ThreadLocalRandom.current().nextLong();

        var nonExpungeableCharge2 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId4)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC)
                        .minusDays(92))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id_13")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        insertChargeEvent(nonExpungeableCharge2);
        ledgerStub.returnLedgerTransaction("external_charge_id_13", nonExpungeableCharge2, null);

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);

        var postCharge10 = databaseTestHelper.containsChargeWithExternalId("external_charge_id_10");
        assertThat(postCharge10, is(true));
        var postCharge11 = databaseTestHelper.containsChargeWithExternalId("external_charge_id_11");
        assertThat(postCharge11, is(false));
        var postCharge12 = databaseTestHelper.containsChargeWithExternalId("external_charge_id_12");
        assertThat(postCharge12, is(true));
        var postCharge13 = databaseTestHelper.containsChargeWithExternalId("external_charge_id_13");
        assertThat(postCharge13, is(false));
    }

    @Test
    public void shouldExpungeAuxiliaryTables_whenTheyExistAndReferenceAChargeForExpunging() throws JsonProcessingException {
        var chargedId = ThreadLocalRandom.current().nextLong();

        ledgerStub = new LedgerStub();
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(chargedId)
                .withCreatedDate(parse(ISO_INSTANT_MILLISECOND_PRECISION.format(now(UTC).minusDays(91))))
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id")
                .withTransactionId("gateway_transaction_id")
                .withLanguage(ENGLISH)
                .withEmail("test@test.test")
                .withDescription("a description")
                .withReference(ServicePaymentReference.of("a reference"))
                .withAmount(2500)
                .withReturnUrl("https://www.test.test/")
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
        DatabaseFixtures.TestFee testFee = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper).aTestFee()
                .withTestCharge(expungeableCharge1)
                .withFeeDue(0L)
                .withFeeCollected(0L);
        testFee.insert();
        DatabaseFixtures.TestToken testToken = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper).aTestToken()
                .withCharge(expungeableCharge1)
                .withUsed(false)
                .insert();
        testToken.insert();
        insertChargeEvent(expungeableCharge1);
        emittedEventDao.persist(anEmittedEventEntity()
                .withResourceExternalId(expungeableCharge1.getExternalChargeId())
                .withResourceType("payment")
                .withId(RandomUtils.nextLong())
                .build());

        ledgerStub.returnLedgerTransaction("external_charge_id", expungeableCharge1, testFee);
        var charge = databaseTestHelper.containsChargeWithExternalId("external_charge_id");
        assertThat(charge, is(true));
        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);

        var postCharge = databaseTestHelper.containsChargeWithExternalId("external_charge_id");
        assertThat(postCharge, is(false));
        var postToken = databaseTestHelper.containsTokenWithChargeId(expungeableCharge1.getChargeId());
        assertThat(postToken, is(false));
        var postFee = databaseTestHelper.containsFeeWithChargeId(expungeableCharge1.getChargeId());
        assertThat(postFee, is(false));
        var postEmittedEvents = databaseTestHelper.containsEmittedEventWithExternalId(expungeableCharge1.getExternalChargeId());
        assertThat(postEmittedEvents, is(false));
    }

    @Test
    public void shouldExpungeRefundEligibleAndRelatedRecords() throws JsonProcessingException {
        String chargeExternalId = randomAlphanumeric(26);
        RefundEntity refundToBeExpunged1 = createAndStubRefund(chargeExternalId, 91, REFUNDED, true);
        RefundEntity refundToBeExpunged2 = createAndStubRefund(chargeExternalId, 91, REFUND_SUBMITTED, true);

        boolean containsRefund1 = databaseTestHelper.containsRefundWithExternalId(refundToBeExpunged1.getExternalId());
        assertThat(containsRefund1, is(true));
        boolean containsRefund2 = databaseTestHelper.containsRefundWithExternalId(refundToBeExpunged2.getExternalId());
        assertThat(containsRefund2, is(true));

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);

        containsRefund1 = databaseTestHelper.containsRefundWithExternalId(refundToBeExpunged1.getExternalId());
        containsRefund2 = databaseTestHelper.containsRefundWithExternalId(refundToBeExpunged2.getExternalId());
        assertThat(containsRefund1, is(false));
        assertThat(containsRefund2, is(false));
    }

    @Test
    public void shouldNotExpungeRefundsNotMeetingCriteria() throws JsonProcessingException {
        String chargeExternalId = randomAlphanumeric(26);
        RefundEntity nonExpungeableRefund1 = createAndStubRefund(randomAlphanumeric(26), 1, REFUNDED, true);
        RefundEntity nonExpungeableRefund2 = createAndStubRefund(randomAlphanumeric(26), 1, REFUND_SUBMITTED, true);
        RefundEntity parityCheckFailedRefund = createAndStubRefund(chargeExternalId, 91, REFUNDED, false);

        var chargeToStub = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(ThreadLocalRandom.current().nextLong())
                .withTestAccount(defaultTestAccount);
        ledgerStub.returnLedgerTransaction(chargeExternalId, chargeToStub, null);
        var containsEmittedEvents = databaseTestHelper.containsEmittedEventWithExternalId(parityCheckFailedRefund.getExternalId());
        assertThat(containsEmittedEvents, is(false));

        given().port(testContext.getPort())
                .contentType(JSON)
                .post("/v1/tasks/expunge")
                .then()
                .statusCode(200);

        boolean containsRefund1 = databaseTestHelper.containsRefundWithExternalId(nonExpungeableRefund1.getExternalId());
        assertThat(containsRefund1, is(true));
        boolean containsRefund2 = databaseTestHelper.containsRefundWithExternalId(nonExpungeableRefund2.getExternalId());
        assertThat(containsRefund2, is(true));

        List<Map<String, Object>> parityCheckFailedRefundFromDB = databaseTestHelper.getRefund(parityCheckFailedRefund.getId());
        assertThat(parityCheckFailedRefundFromDB.get(0).get("external_id"), is(parityCheckFailedRefund.getExternalId()));
        assertThat(parityCheckFailedRefundFromDB.get(0).get("parity_check_status"), is("DATA_MISMATCH"));
        assertThat(parityCheckFailedRefundFromDB.get(0).get("parity_check_date"), is(notNullValue()));
        containsEmittedEvents = databaseTestHelper.containsEmittedEventWithExternalId(parityCheckFailedRefund.getExternalId());
        assertThat(containsEmittedEvents, is(true));

        List<Map<String, Object>> refundsHistoryList = databaseTestHelper.getRefundsHistoryByChargeExternalId(chargeExternalId);
        assertThat(refundsHistoryList.size(), is(2));
    }

    private RefundEntity createAndStubRefund(String chargeExternalId, int createdBeforeDays, RefundStatus refundStatus,
                                             boolean stubForMatchingLedgerTransaction) throws JsonProcessingException {
        RefundEntity refundEntity = new RefundEntity(100L, userExternalId, userEmail, chargeExternalId);
        refundEntity.setGatewayTransactionId(randomAlphanumeric(20));
        refundEntity.setStatus(RefundStatus.CREATED);
        refundEntity.setCreatedDate(now(UTC).minusDays(createdBeforeDays));
        refundDao.persist(refundEntity);
        refundEntity.setStatus(refundStatus);
        refundDao.merge(refundEntity);
        Optional<RefundHistory> refundHistoryCreated = refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                refundEntity.getExternalId(), RefundStatus.CREATED);

        ledgerStub = new LedgerStub();

        if (stubForMatchingLedgerTransaction) {
            ledgerStub.returnLedgerTransaction(refundEntity, defaultTestAccount.getAccountId(),
                    refundHistoryCreated.get().getHistoryStartDate());
        } else {
            ledgerStub.returnLedgerTransactionWithMismatch(refundEntity, refundHistoryCreated.get().getHistoryStartDate());
        }

        return refundEntity;
    }
}
