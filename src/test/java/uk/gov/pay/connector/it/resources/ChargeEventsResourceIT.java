package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.matcher.TransactionEventMatcher;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.dao.DatabaseFixtures.withDatabaseTestHelper;
import static uk.gov.pay.connector.matcher.TransactionEventMatcher.withState;
import static uk.gov.pay.connector.util.AddGatewayAccountParams.AddGatewayAccountParamsBuilder.anAddGatewayAccountParams;

public class ChargeEventsResourceIT {
    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");

    @BeforeAll
    public static void setUp() {
        app.setUpBase();
    }

    public static final String SUBMITTED_BY = "r378y387y8weriyi";
    public static final String USER_EMAIL = "test@test.com";
    private String gatewayRefundTransactionId;
    private String accountId = "72332423443245";

    private AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
            .withAccountId(accountId)
            .withPaymentGateway("sandbox")
            .withServiceName("a cool service")
            .build();

    @BeforeEach
    public void setUpRefund() {
        gatewayRefundTransactionId = RandomStringUtils.randomAlphanumeric(30);
    }

    @AfterEach
    public void teardown() {
        app.getDatabaseTestHelper().truncateAllData();
    }

    @Test
    public void shouldGetAllEventsForAGivenChargeWithoutRefunds() {
        ZonedDateTime createdDate = ZonedDateTime.now();
        String externalChargeId = "external-charge-id";

        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        DatabaseFixtures.TestCharge testCharge = createTestCharge(externalChargeId).insert();

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

        var result = app.getDatabaseTestHelper().getChargeEvents(testCharge.getChargeId());

        app.getConnectorRestApiClient()
                .withAccountId(accountId)
                .getEvents(testCharge.getExternalChargeId())
                .body("charge_id", is(testCharge.getExternalChargeId()))
                .body("events.size()", equalTo(3))
                .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()));
    }

    @Test
    public void shouldGetAllEventsForAGivenChargeWithRefunds() {
        ZonedDateTime createdDate = ZonedDateTime.now();
        String externalChargeId = "an-external-charge-id";

        app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

        DatabaseFixtures.TestCharge testCharge = createTestCharge(externalChargeId).insert();

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

        String testGatewayReferenceRefund2 = RandomStringUtils.randomAlphanumeric(10);

        ZonedDateTime refundTest1RefundedDate = createdDate.plusSeconds(9);
        DatabaseFixtures.TestRefund refundedTestRefund1 = createTestRefund(testCharge)
                .withAmount(10L)
                .withGatewayTransactionId(gatewayRefundTransactionId)
                .withType(RefundStatus.REFUNDED)
                .withCreatedDate(refundTest1RefundedDate)
                .withSubmittedBy(SUBMITTED_BY)
                .withChargeExternalId(testCharge.getExternalChargeId())
                .insert();

        ZonedDateTime refundTest2RefundedDate = createdDate.plusSeconds(12);
        DatabaseFixtures.TestRefund refundedTestRefund2 = createTestRefund(testCharge)
                .withAmount(90L)
                .withType(RefundStatus.REFUNDED)
                .withGatewayTransactionId(testGatewayReferenceRefund2)
                .withCreatedDate(refundTest2RefundedDate)
                .withChargeExternalId(testCharge.getExternalChargeId())
                .insert();

        ZonedDateTime historyRefund1SubmittedStartDate = createdDate.plusSeconds(8);
        createTestRefundHistory(refundedTestRefund1)
                .insert(RefundStatus.CREATED, createdDate.plusSeconds(7), historyRefund1SubmittedStartDate, SUBMITTED_BY, USER_EMAIL)
                .insert(RefundStatus.REFUND_SUBMITTED, gatewayRefundTransactionId, historyRefund1SubmittedStartDate, refundTest1RefundedDate, SUBMITTED_BY, USER_EMAIL)
                .insert(RefundStatus.REFUNDED, gatewayRefundTransactionId, refundTest1RefundedDate, SUBMITTED_BY, testCharge.getExternalChargeId());

        ZonedDateTime historyRefund2SubmittedStartDate = createdDate.plusSeconds(11);
        createTestRefundHistory(refundedTestRefund2)
                .insert(RefundStatus.CREATED, createdDate.plusSeconds(10), historyRefund2SubmittedStartDate)
                .insert(RefundStatus.REFUND_SUBMITTED, testGatewayReferenceRefund2, historyRefund2SubmittedStartDate, refundTest2RefundedDate)
                .insert(RefundStatus.REFUNDED, testGatewayReferenceRefund2, refundTest2RefundedDate);

        List<Map<String, Object>> charges = app.getDatabaseTestHelper().getChargeEvents(testCharge.getChargeId());
        List<Map<String, Object>> refunds = app.getDatabaseTestHelper().getRefundsHistoryByChargeExternalId(testCharge.getExternalChargeId());
        app.getConnectorRestApiClient()
                .getEvents(testCharge.getExternalChargeId())
                .body("charge_id", is(testCharge.getExternalChargeId()))
                .body("events.size()", equalTo(7))
                .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()))
                .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", historyRefund1SubmittedStartDate, gatewayRefundTransactionId, SUBMITTED_BY))
                .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", refundTest1RefundedDate, gatewayRefundTransactionId, SUBMITTED_BY))
                .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", historyRefund2SubmittedStartDate, testGatewayReferenceRefund2, null))
                .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", refundTest2RefundedDate, testGatewayReferenceRefund2, null));
    }

    @Test
    public void shouldReturn404WhenAccountIdIsNonNumeric() {
        app.getConnectorRestApiClient().withAccountId("invalidAccountId")
                .getEvents("123charge")
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("code", is(404))
                .body("message", is("HTTP 404 Not Found"));
    }

    @Test
    public void shouldReturn404WhenIsChargeIdNotExists() {
        app.getConnectorRestApiClient().withAccountId(accountId)
                .getEvents("non-existent-charge")
                .contentType(JSON)
                .statusCode(NOT_FOUND.getStatusCode())
                .body("message", contains("Charge with id [non-existent-charge] not found."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private DatabaseFixtures.TestCharge createTestCharge(String externalChargeId) {
        DatabaseFixtures.TestAccount testAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(Long.valueOf(accountId));

        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withExternalChargeId(externalChargeId)
                .withAmount(100L)
                .withTestAccount(testAccount)
                .withChargeStatus(CAPTURED);
    }

    private DatabaseFixtures.TestChargeEvent createTestChargeEvent(DatabaseFixtures.TestCharge testCharge) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestChargeEvent()
                .withTestCharge(testCharge);
    }

    private DatabaseFixtures.TestRefundHistory createTestRefundHistory(DatabaseFixtures.TestRefund testRefund) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestRefundHistory(testRefund);
    }

    private DatabaseFixtures.TestRefund createTestRefund(DatabaseFixtures.TestCharge testCharge) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestRefund()
                .withTestCharge(testCharge);
    }
}
