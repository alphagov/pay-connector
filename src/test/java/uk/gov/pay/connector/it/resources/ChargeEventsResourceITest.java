package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.matcher.TransactionEventMatcher;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.DatabaseTestHelper;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZonedDateTime;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.matcher.TransactionEventMatcher.withState;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

public class ChargeEventsResourceITest {

    public static final String SUBMITTED_BY = "r378y387y8weriyi";
    private DatabaseTestHelper databaseTestHelper;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private RestAssuredClient connectorApi;

    @Before
    public void setUp() {
        databaseTestHelper = app.getDatabaseTestHelper();
        connectorApi = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "sandbox");
    }


    @Test
    public void shouldGetAllEventsForAGivenChargeWithoutRefunds() throws Exception {
        ZonedDateTime createdDate = ZonedDateTime.now();

        DatabaseFixtures.TestCharge testCharge = createTestCharge().insert();

        DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(CREATED).withDate(createdDate).insert();
        DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
        DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_READY).withDate(createdDate.plusSeconds(4)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_SUBMITTED).withDate(createdDate.plusSeconds(5)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURED).withDate(createdDate.plusSeconds(6)).insert();

        connectorApi
                .getEvents(testCharge.getExternalChargeId())
                .body("charge_id", is(testCharge.getExternalChargeId()))
                .body("events.size()", equalTo(3))
                .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()));
    }

    @Test
    public void shouldGetAllEventsForAGivenChargeWithRefunds() throws Exception {

        ZonedDateTime createdDate = ZonedDateTime.now();

        DatabaseFixtures.TestCharge testCharge = createTestCharge().insert();

        DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(CREATED).withDate(createdDate).insert();
        DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();

        createTestChargeEvent(testCharge)
                .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
        DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_READY).withDate(createdDate.plusSeconds(4)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_SUBMITTED).withDate(createdDate.plusSeconds(5)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURED).withDate(createdDate.plusSeconds(6)).insert();

        String testReferenceRefund1 = RandomStringUtils.randomAlphanumeric(10);
        String testReferenceRefund2 = RandomStringUtils.randomAlphanumeric(10);

        ZonedDateTime refundTest1RefundedDate = createdDate.plusSeconds(9);
        DatabaseFixtures.TestRefund refundedTestRefund1 = createTestRefund(testCharge)
                .withAmount(10L)
                .withReference(testReferenceRefund1)
                .withType(RefundStatus.REFUNDED)
                .withCreatedDate(refundTest1RefundedDate)
                .withSubmittedBy(SUBMITTED_BY)
                .insert();

        ZonedDateTime refundTest2RefundedDate = createdDate.plusSeconds(12);
        DatabaseFixtures.TestRefund refundedTestRefund2 = createTestRefund(testCharge)
                .withAmount(90L)
                .withType(RefundStatus.REFUNDED)
                .withReference(testReferenceRefund2)
                .withCreatedDate(refundTest2RefundedDate)
                .insert();

        ZonedDateTime historyRefund1SubmittedStartDate = createdDate.plusSeconds(8);
        createTestRefundHistory(refundedTestRefund1)
                .insert(RefundStatus.CREATED, createdDate.plusSeconds(7), historyRefund1SubmittedStartDate, SUBMITTED_BY)
                .insert(RefundStatus.REFUND_SUBMITTED, testReferenceRefund1, historyRefund1SubmittedStartDate, refundTest1RefundedDate, SUBMITTED_BY)
                .insert(RefundStatus.REFUNDED, testReferenceRefund1, refundTest1RefundedDate, SUBMITTED_BY);

        ZonedDateTime historyRefund2SubmittedStartDate = createdDate.plusSeconds(11);
        createTestRefundHistory(refundedTestRefund2)
                .insert(RefundStatus.CREATED, createdDate.plusSeconds(10), historyRefund2SubmittedStartDate)
                .insert(RefundStatus.REFUND_SUBMITTED, testReferenceRefund2, historyRefund2SubmittedStartDate, refundTest2RefundedDate)
                .insert(RefundStatus.REFUNDED, testReferenceRefund2, refundTest2RefundedDate);

        connectorApi
                .getEvents(testCharge.getExternalChargeId())
                .body("charge_id", is(testCharge.getExternalChargeId()))
                .body("events.size()", equalTo(7))
                .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()))
                .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", historyRefund1SubmittedStartDate, testReferenceRefund1, SUBMITTED_BY))
                .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", refundTest1RefundedDate, testReferenceRefund1, SUBMITTED_BY))
                .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", historyRefund2SubmittedStartDate, testReferenceRefund2, null))
                .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", refundTest2RefundedDate, testReferenceRefund2, null));
    }

    @Test
    public void shouldReturn404WhenAccountIdIsNonNumeric() {
        connectorApi.withAccountId("invalidAccountId")
                .getEvents("123charge")
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404WhenIsChargeIdNotExists() {
        connectorApi.withAccountId(accountId)
                .getEvents("non-existent-charge")
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", is("Charge with id [non-existent-charge] not found."));
    }

    private DatabaseFixtures.TestCharge createTestCharge() {
        DatabaseFixtures.TestAccount testAccount = withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withAccountId(Long.valueOf(accountId));

        return withDatabaseTestHelper(databaseTestHelper)
                        .aTestCharge()
                        .withAmount(100L)
                        .withTestAccount(testAccount)
                        .withChargeStatus(CAPTURED);
    }

    private DatabaseFixtures.TestChargeEvent createTestChargeEvent(DatabaseFixtures.TestCharge testCharge) {
        return withDatabaseTestHelper(databaseTestHelper)
                        .aTestChargeEvent()
                        .withTestCharge(testCharge);
    }

    private DatabaseFixtures.TestRefundHistory createTestRefundHistory(DatabaseFixtures.TestRefund testRefund) {
        return withDatabaseTestHelper(databaseTestHelper)
                .aTestRefundHistory(testRefund);
    }

    private DatabaseFixtures.TestRefund createTestRefund(DatabaseFixtures.TestCharge testCharge) {
        return withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(testCharge);
    }
}
