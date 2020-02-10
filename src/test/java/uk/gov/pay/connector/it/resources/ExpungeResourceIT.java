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

import java.time.ZonedDateTime;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static uk.gov.pay.commons.model.SupportedLanguage.ENGLISH;
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
        ledgerStub = new LedgerStub();
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(1L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(91))
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
    
    @Test
    public void shouldUpdateTheParityCheckedDateOfNonCriteriaMatchedCharge() throws JsonProcessingException {
        ledgerStub = new LedgerStub();
        var date = ZonedDateTime.now(UTC).minusDays(91);
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(1L)
                .withCreatedDate(date)
                .withTestAccount(defaultTestAccount)
                .withExternalChargeId("external_charge_id")
                .withAmount(2500)
                .withChargeStatus(ChargeStatus.CAPTURED)
                .insert();
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
        ledgerStub = new LedgerStub();
        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(3L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(89))
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
        ledgerStub = new LedgerStub();

        expungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(10L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(89))
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
        ledgerStub.returnLedgerTransaction("external_charge_id_10", expungeableCharge1);

        var nonExpungeableCharge1 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(11L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(91))
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
        ledgerStub.returnLedgerTransaction("external_charge_id_11", nonExpungeableCharge1);

        var expungeableCharge2 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(12L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(88))
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
        ledgerStub.returnLedgerTransaction("external_charge_id_12", expungeableCharge2);

        var nonExpungeableCharge2 = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withChargeId(13L)
                .withCreatedDate(ZonedDateTime.now(UTC).minusDays(92))
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
