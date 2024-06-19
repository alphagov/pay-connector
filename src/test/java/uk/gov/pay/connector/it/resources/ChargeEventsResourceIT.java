package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.matcher.TransactionEventMatcher;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.AddGatewayAccountParams;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZonedDateTime;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
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
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    public static final String SUBMITTED_BY = "r378y387y8weriyi";
    public static final String USER_EMAIL = "test@test.com";
    private static final String SERVICE_ID = "a-valid-service-id";
    private static final String CHARGE_ID = "external-charge-id";
    private static final String CHARGE_EVENTS_BY_GATEWAY_ACCOUNT_ID_URL = "/v1/api/accounts/%s/charges/%s/events";
    private static final String CHARGE_EVENTS_BY_SERVICE_ID_URL = "/v1/api/service/%s/accountType/%s/charges/%s/events";
    private static final String ACCOUNT_ID = "72332423443245";


    private AddGatewayAccountParams gatewayAccountParams = anAddGatewayAccountParams()
            .withAccountId(ACCOUNT_ID)
            .withPaymentGateway("sandbox")
            .withServiceName("a cool service")
            .withServiceId(SERVICE_ID)
            .build();
    
    @Nested
    class ByServiceIdAndAccountType {
        @Test
        public void shouldGetAllEventsForAGivenChargeWithoutRefunds() {
            ZonedDateTime createdDate = ZonedDateTime.now();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
            DatabaseFixtures.TestCharge testCharge = createTestCharge(CHARGE_ID).insert();
            DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CREATED).withDate(createdDate).insert();
            DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();
            createTestChargeEvent(testCharge)
                    .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
            DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
            createCaptureEvents(testCharge, createdDate);

            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_SERVICE_ID_URL, SERVICE_ID, GatewayAccountType.TEST, CHARGE_ID))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("charge_id", is(CHARGE_ID))
                    .body("events.size()", equalTo(3))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()));
        }

        @Test
        public void shouldGetAllEventsForAGivenChargeWithRefunds() {
            ZonedDateTime createdDate = ZonedDateTime.now();
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
            DatabaseFixtures.TestCharge testCharge = createTestCharge(CHARGE_ID).insert();
            DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CREATED).withDate(createdDate).insert();
            DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();
            createTestChargeEvent(testCharge)
                    .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
            DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
            createCaptureEvents(testCharge, createdDate);
    
            String gatewayTransactionIdForRefund1 = RandomStringUtils.randomAlphanumeric(30);
            String gatewayTransactionIdForRefund2 = RandomStringUtils.randomAlphanumeric(10);
    
            ZonedDateTime testRefund1CreatedDate = createdDate.plusSeconds(9);
            DatabaseFixtures.TestRefund testRefund1 = createTestRefund(testCharge)
                    .withAmount(10L)
                    .withGatewayTransactionId(gatewayTransactionIdForRefund1)
                    .withType(RefundStatus.REFUNDED)
                    .withCreatedDate(testRefund1CreatedDate)
                    .withSubmittedBy(SUBMITTED_BY)
                    .withChargeExternalId(CHARGE_ID)
                    .insert();
    
            ZonedDateTime testRefund2CreatedDate = createdDate.plusSeconds(12);
            DatabaseFixtures.TestRefund testRefund2 = createTestRefund(testCharge)
                    .withAmount(90L)
                    .withType(RefundStatus.REFUNDED)
                    .withGatewayTransactionId(gatewayTransactionIdForRefund2)
                    .withCreatedDate(testRefund2CreatedDate)
                    .withChargeExternalId(CHARGE_ID)
                    .insert();
    
            ZonedDateTime historyRefund1SubmittedStartDate = createdDate.plusSeconds(8);
            createTestRefundHistory(testRefund1)
                    .insert(RefundStatus.CREATED, createdDate.plusSeconds(7), historyRefund1SubmittedStartDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForRefund1, historyRefund1SubmittedStartDate, testRefund1CreatedDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForRefund1, testRefund1CreatedDate, SUBMITTED_BY, testCharge.getExternalChargeId());
    
            ZonedDateTime historyRefund2SubmittedStartDate = createdDate.plusSeconds(11);
            createTestRefundHistory(testRefund2)
                    .insert(RefundStatus.CREATED, createdDate.plusSeconds(10), historyRefund2SubmittedStartDate)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForRefund2, historyRefund2SubmittedStartDate, testRefund2CreatedDate)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForRefund2, testRefund2CreatedDate);
    
            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_SERVICE_ID_URL, SERVICE_ID, GatewayAccountType.TEST, CHARGE_ID))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("charge_id", is(testCharge.getExternalChargeId()))
                    .body("events.size()", equalTo(7))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()))
                    .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", historyRefund1SubmittedStartDate, gatewayTransactionIdForRefund1, SUBMITTED_BY))
                    .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", testRefund1CreatedDate, gatewayTransactionIdForRefund1, SUBMITTED_BY))
                    .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", historyRefund2SubmittedStartDate, gatewayTransactionIdForRefund2, null))
                    .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", testRefund2CreatedDate, gatewayTransactionIdForRefund2, null));
        }

        @Test
        public void shouldReturn404WhenServiceIdIsIncorrect() {
            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);
            createTestCharge(CHARGE_ID).insert();
            
            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_SERVICE_ID_URL, "incorrect-service-id", GatewayAccountType.TEST, CHARGE_ID))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message", contains(format("Charge with id [%s] not found.", CHARGE_ID)))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    
        @Test
        public void shouldReturn404WhenChargeIdDoesNotExist() {
            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_SERVICE_ID_URL, SERVICE_ID, GatewayAccountType.TEST, "non-existent-charge"))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message", contains("Charge with id [non-existent-charge] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    @Nested
    class ByGatewayAccountId {
        @Test
        public void shouldGetAllEventsForAGivenChargeWithoutRefunds() {
            ZonedDateTime createdDate = ZonedDateTime.now();

            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            DatabaseFixtures.TestCharge testCharge = createTestCharge(CHARGE_ID).insert();

            DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CREATED).withDate(createdDate).insert();
            DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();
            createTestChargeEvent(testCharge)
                    .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
            DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
            createCaptureEvents(testCharge, createdDate);

            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_GATEWAY_ACCOUNT_ID_URL, ACCOUNT_ID, CHARGE_ID))
                    .then()
                    .body("charge_id", is(testCharge.getExternalChargeId()))
                    .body("events.size()", equalTo(3))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()));
        }

        @Test
        public void shouldGetAllEventsForAGivenChargeWithRefunds() {
            ZonedDateTime createdDate = ZonedDateTime.now();

            app.getDatabaseTestHelper().addGatewayAccount(gatewayAccountParams);

            DatabaseFixtures.TestCharge testCharge = createTestCharge(CHARGE_ID).insert();

            DatabaseFixtures.TestChargeEvent createdTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CREATED).withDate(createdDate).insert();
            DatabaseFixtures.TestChargeEvent enteringCardDetailsTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(ENTERING_CARD_DETAILS).withDate(createdDate.plusSeconds(1)).insert();

            createTestChargeEvent(testCharge)
                    .withChargeStatus(AUTHORISATION_READY).withDate(createdDate.plusSeconds(2)).insert();
            DatabaseFixtures.TestChargeEvent captureApprovedTestChargeEvent = createTestChargeEvent(testCharge)
                    .withChargeStatus(CAPTURE_APPROVED).withDate(createdDate.plusSeconds(3)).insert();
            createCaptureEvents(testCharge, createdDate);

            String gatewayTransactionIdForRefund1 = RandomStringUtils.randomAlphanumeric(30);
            String gatewayTransactionIdForRefund2 = RandomStringUtils.randomAlphanumeric(10);

            ZonedDateTime testRefund1CreatedDate = createdDate.plusSeconds(9);
            DatabaseFixtures.TestRefund testRefund1 = createTestRefund(testCharge)
                    .withAmount(10L)
                    .withGatewayTransactionId(gatewayTransactionIdForRefund1)
                    .withType(RefundStatus.REFUNDED)
                    .withCreatedDate(testRefund1CreatedDate)
                    .withSubmittedBy(SUBMITTED_BY)
                    .withChargeExternalId(CHARGE_ID)
                    .insert();

            ZonedDateTime testRefund2CreatedDate = createdDate.plusSeconds(12);
            DatabaseFixtures.TestRefund testRefund2 = createTestRefund(testCharge)
                    .withAmount(90L)
                    .withType(RefundStatus.REFUNDED)
                    .withGatewayTransactionId(gatewayTransactionIdForRefund2)
                    .withCreatedDate(testRefund2CreatedDate)
                    .withChargeExternalId(CHARGE_ID)
                    .insert();

            ZonedDateTime historyRefund1SubmittedStartDate = createdDate.plusSeconds(8);
            createTestRefundHistory(testRefund1)
                    .insert(RefundStatus.CREATED, createdDate.plusSeconds(7), historyRefund1SubmittedStartDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForRefund1, historyRefund1SubmittedStartDate, testRefund1CreatedDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForRefund1, testRefund1CreatedDate, SUBMITTED_BY, testCharge.getExternalChargeId());

            ZonedDateTime historyRefund2SubmittedStartDate = createdDate.plusSeconds(11);
            createTestRefundHistory(testRefund2)
                    .insert(RefundStatus.CREATED, createdDate.plusSeconds(10), historyRefund2SubmittedStartDate)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForRefund2, historyRefund2SubmittedStartDate, testRefund2CreatedDate)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForRefund2, testRefund2CreatedDate);

            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_GATEWAY_ACCOUNT_ID_URL, ACCOUNT_ID, CHARGE_ID))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("charge_id", is(testCharge.getExternalChargeId()))
                    .body("events.size()", equalTo(7))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdTestChargeEvent.getUpdated()))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsTestChargeEvent.getUpdated()))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedTestChargeEvent.getUpdated()))
                    .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", historyRefund1SubmittedStartDate, gatewayTransactionIdForRefund1, SUBMITTED_BY))
                    .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", testRefund1CreatedDate, gatewayTransactionIdForRefund1, SUBMITTED_BY))
                    .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", historyRefund2SubmittedStartDate, gatewayTransactionIdForRefund2, null))
                    .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", testRefund2CreatedDate, gatewayTransactionIdForRefund2, null));
        }

        @Test
        public void shouldReturn404WhenAccountIdIsNonNumeric() {
            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_GATEWAY_ACCOUNT_ID_URL, "invalid-account-id", CHARGE_ID))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("code", is(404))
                    .body("message", is("HTTP 404 Not Found"));
        }

        @Test
        public void shouldReturn404WhenChargeIdDoesNotExist() {
            app.givenSetup()
                    .get(format(CHARGE_EVENTS_BY_GATEWAY_ACCOUNT_ID_URL, ACCOUNT_ID, "non-existent-charge"))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message", contains("Charge with id [non-existent-charge] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    private DatabaseFixtures.TestCharge createTestCharge(String externalChargeId) {
        DatabaseFixtures.TestAccount testAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(Long.valueOf(ACCOUNT_ID));

        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withExternalChargeId(externalChargeId)
                .withAmount(100L)
                .withServiceId(SERVICE_ID)
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

    private void createCaptureEvents(DatabaseFixtures.TestCharge testCharge, ZonedDateTime createdDate) {
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_READY).withDate(createdDate.plusSeconds(4)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURE_SUBMITTED).withDate(createdDate.plusSeconds(5)).insert();
        createTestChargeEvent(testCharge)
                .withChargeStatus(CAPTURED).withDate(createdDate.plusSeconds(6)).insert();
    }
}
