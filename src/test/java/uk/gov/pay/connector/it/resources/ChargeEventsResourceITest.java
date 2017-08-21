package uk.gov.pay.connector.it.resources;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.matcher.TransactionEventMatcher;
import uk.gov.pay.connector.model.domain.RefundStatus;
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
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeEventsResourceITest {

    private DatabaseTestHelper databaseTestHelper;

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private RestAssuredClient connectorApi = new RestAssuredClient(app, accountId);

    @Before
    public void setUp() throws Exception {
        databaseTestHelper = app.getDatabaseTestHelper();
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

        DatabaseFixtures.TestRefund createdTestRefund1 = createTestRefund(testCharge)
                .withAmount(10L)
                .withType(RefundStatus.CREATED)
                .withHistoryStartDate(createdDate.plusSeconds(7))
                .insertHistory();

        createTestRefund(testCharge)
                .withAmount(10L)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .withHistoryStartDate(createdDate.plusSeconds(8))
                .insertHistory();

        DatabaseFixtures.TestRefund createdTestRefund2 = createTestRefund(testCharge)
                .withAmount(90L)
                .withType(RefundStatus.CREATED)
                .withHistoryStartDate(createdDate.plusSeconds(9))
                .insertHistory();

        createTestRefund(testCharge)
                .withAmount(90L)
                .withType(RefundStatus.REFUND_SUBMITTED)
                .withHistoryStartDate(createdDate.plusSeconds(10))
                .insertHistory();

        DatabaseFixtures.TestRefund refundedTestRefund2 = createTestRefund(testCharge)
                .withAmount(90L)
                .withType(RefundStatus.REFUNDED)
                .withCreatedDate(createdDate.plusSeconds(7))
                .withHistoryStartDate(createdDate.plusSeconds(11))
                .insert()
                .insertHistory();

        DatabaseFixtures.TestRefund refundedTestRefund1 = createTestRefund(testCharge)
                .withAmount(10L)
                .withType(RefundStatus.REFUNDED)
                .withCreatedDate(createdDate.plusSeconds(9))
                .withHistoryStartDate(createdDate.plusSeconds(12))
                .insert()
                .insertHistory();

        connectorApi
                .getEvents(testCharge.getExternalChargeId())
                .body("charge_id", is(testCharge.getExternalChargeId()))
                .body("events.size()", equalTo(7))
                .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()))
                .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", createdTestRefund1.getHistoryStartDate(), createdTestRefund1.getReference()))
                .body("events[4]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", createdTestRefund2.getHistoryStartDate(), createdTestRefund2.getReference()))
                .body("events[5]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", refundedTestRefund2.getHistoryStartDate(), refundedTestRefund2.getReference()))
                .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", refundedTestRefund1.getHistoryStartDate(), refundedTestRefund1.getReference()));
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

    private DatabaseFixtures.TestRefund createTestRefund(DatabaseFixtures.TestCharge testCharge) {
        return withDatabaseTestHelper(databaseTestHelper)
                .aTestRefund()
                .withTestCharge(testCharge);
    }
}
