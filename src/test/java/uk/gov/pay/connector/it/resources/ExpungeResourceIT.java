package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.expunge.service.LedgerStub;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.now;
import static java.time.ZonedDateTime.parse;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;
import static uk.gov.pay.commons.model.SupportedLanguage.ENGLISH;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.junit.DropwizardJUnitRunner.WIREMOCK_PORT;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class ExpungeResourceIT {
    
    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(WIREMOCK_PORT);
    
    @DropwizardTestContext
    protected TestContext testContext;
    
    private DatabaseFixtures.TestCharge expungeableCharge1;
    private DatabaseTestHelper databaseTestHelper;
    private DatabaseFixtures.TestAccount defaultTestAccount;
    
    private LedgerStub ledgerStub;


    @Before
    public void setUp() {
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
        ledgerStub.returnLedgerTransaction("external_charge_id", expungeableCharge1);
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
        ledgerStub.returnLedgerTransactionWithMismatch("external_charge_id", expungeableCharge1);
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
        ledgerStub.returnLedgerTransaction("external_charge_id", expungeableCharge1);
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
        ledgerStub.returnLedgerTransaction("external_charge_id_10", expungeableCharge1);

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
        ledgerStub.returnLedgerTransaction("external_charge_id_11", nonExpungeableCharge1);

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
        ledgerStub.returnLedgerTransaction("external_charge_id_12", expungeableCharge2);

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
        ledgerStub.returnLedgerTransaction("external_charge_id_13", nonExpungeableCharge2);
        
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
}
