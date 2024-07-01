package uk.gov.pay.connector.it.resources;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.matcher.TransactionEventMatcher;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

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
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargeEventsResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("sandbox", app.getLocalPort(), app.getDatabaseTestHelper());

    public static final String SUBMITTED_BY = "r378y387y8weriyi";
    public static final String USER_EMAIL = "test@test.com";
    private static final String SERVICE_ID = "a-valid-service-id";
    private static final String CHARGE_EXTERNAL_ID = "external-charge-id";
    
    private String gatewayAccountId;
    private long chargeId;
    private DatabaseFixtures.TestCharge testCharge;

    @BeforeEach
    void setup() {
        //create the gateway account via the API
        gatewayAccountId = app.givenSetup()
                .body(toJson(Map.of(
                        "service_id", SERVICE_ID,
                        "type", GatewayAccountType.TEST,
                        "payment_provider", PaymentGatewayName.STRIPE.getName(),
                        "service_name", "MyTestService"
                )))
                .post("/v1/api/accounts")
                .then()
                .statusCode(201)
                .extract().path("gateway_account_id");


        //create a £1 payment for that account
        DatabaseFixtures.TestAccount testAccount = withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .withAccountId(Long.parseLong(gatewayAccountId));
        chargeId = RandomUtils.nextLong();
        testCharge = withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withChargeId(chargeId)
                .withExternalChargeId(CHARGE_EXTERNAL_ID)
                .withAmount(100L)
                .withServiceId(SERVICE_ID)
                .withTestAccount(testAccount)
                .withChargeStatus(CAPTURED)
                .insert();
    }
    
    @Nested
    class ByServiceIdAndAccountType {
        @Test
        public void shouldGetCorrectEventsForAGivenChargeWithoutRefunds() {
            //set up charge events for a successful payment journey that took place yesterday
            ZonedDateTime createdDate = ZonedDateTime.now().minusDays(1);
            ZonedDateTime enteringCardDetailsDate = createdDate.plusSeconds(1);
            ZonedDateTime authorisationReadyDate = enteringCardDetailsDate.plusSeconds(1);
            ZonedDateTime captureApprovedDate = authorisationReadyDate.plusSeconds(1);
            createEventsForSuccessfulPayment(chargeId, createdDate, enteringCardDetailsDate, authorisationReadyDate, captureApprovedDate);

            //verify that a get request returns the correct transaction events
            //each of the charge events added to the database has a distinct internal state but these map to just three external states
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", SERVICE_ID, GatewayAccountType.TEST, CHARGE_EXTERNAL_ID))
                    .then()
                    .statusCode(200)
                    .body("charge_id", is(CHARGE_EXTERNAL_ID))
                    .body("events.size()", equalTo(3))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdDate))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsDate))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedDate));
        }

        @Test
        public void shouldGetAllEventsForAGivenChargeWithRefunds() {
            //set up charge events for a successful payment journey that took place yesterday
            ZonedDateTime createdDate = ZonedDateTime.now().minusDays(1);
            ZonedDateTime enteringCardDetailsDate = createdDate.plusSeconds(1);
            ZonedDateTime authorisationReadyDate = enteringCardDetailsDate.plusSeconds(1);
            ZonedDateTime captureApprovedDate = authorisationReadyDate.plusSeconds(1);
            createEventsForSuccessfulPayment(chargeId, createdDate, enteringCardDetailsDate, authorisationReadyDate, captureApprovedDate);

            //set up a partial refund
            ZonedDateTime partialRefundCreatedDate = createdDate.plusMinutes(10);
            String gatewayTransactionIdForPartialRefund = RandomStringUtils.randomAlphanumeric(30);
            DatabaseFixtures.TestRefund partialRefund = createTestRefund(testCharge, partialRefundCreatedDate, gatewayTransactionIdForPartialRefund, 10L, SUBMITTED_BY);

            //set up the event history for the partial refund
            ZonedDateTime partialRefundCreatedEventDate = partialRefundCreatedDate.plus(1L, ChronoUnit.MILLIS);
            ZonedDateTime partialRefundSubmittedEventDate = partialRefundCreatedDate.plus(2L, ChronoUnit.MILLIS);
            ZonedDateTime partialRefundRefundedEventDate = partialRefundCreatedDate.plus(3L, ChronoUnit.MILLIS);
            createTestRefundHistory(partialRefund)
                    .insert(RefundStatus.CREATED, partialRefundCreatedEventDate, partialRefundSubmittedEventDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForPartialRefund, partialRefundSubmittedEventDate, partialRefundRefundedEventDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForPartialRefund, partialRefundRefundedEventDate, SUBMITTED_BY, testCharge.getExternalChargeId());

            //set up a second partial refund
            ZonedDateTime secondRefundCreatedDate = partialRefundCreatedDate.plusMinutes(1);
            String gatewayTransactionIdForSecondRefund = RandomStringUtils.randomAlphanumeric(10);
            DatabaseFixtures.TestRefund secondRefund = createTestRefund(testCharge, secondRefundCreatedDate, gatewayTransactionIdForSecondRefund, 90L, null);

            //set up the event history for the second refund
            ZonedDateTime secondRefundCreatedEventDate = secondRefundCreatedDate.plus(1L, ChronoUnit.MILLIS);
            ZonedDateTime secondRefundSubmittedEventDate = secondRefundCreatedDate.plus(2L, ChronoUnit.MILLIS);
            ZonedDateTime secondRefundRefundedEventDate = secondRefundCreatedDate.plus(3L, ChronoUnit.MILLIS);
            createTestRefundHistory(secondRefund)
                    .insert(RefundStatus.CREATED, secondRefundCreatedEventDate, secondRefundSubmittedEventDate)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForSecondRefund, secondRefundSubmittedEventDate, secondRefundRefundedEventDate)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForSecondRefund, secondRefundRefundedEventDate);

            //verify that a get request returns the correct transaction events
            //each of the charge events added to the database has a distinct internal state but these map to just three external states
            //each of the refund events added to the database has a distinct internal state but these map to just two external states for each refund
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", SERVICE_ID, GatewayAccountType.TEST, CHARGE_EXTERNAL_ID))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("charge_id", is(testCharge.getExternalChargeId()))
                    .body("events.size()", equalTo(7))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdDate))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsDate))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedDate))
                    .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", partialRefundSubmittedEventDate, gatewayTransactionIdForPartialRefund, SUBMITTED_BY))
                    .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", partialRefundRefundedEventDate, gatewayTransactionIdForPartialRefund, SUBMITTED_BY))
                    .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", secondRefundSubmittedEventDate, gatewayTransactionIdForSecondRefund, null))
                    .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", secondRefundRefundedEventDate, gatewayTransactionIdForSecondRefund, null));
            }

        @Test
        public void shouldReturn404WhenServiceIdIsIncorrect() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", "incorrect-service-id", GatewayAccountType.TEST, CHARGE_EXTERNAL_ID))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message", contains("Charge with id [external-charge-id] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        public void shouldReturn404WhenChargeIdDoesNotExist() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/%s/charges/%s/events", SERVICE_ID, GatewayAccountType.TEST, "non-existent-charge"))
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
        public void shouldGetCorrectEventsForAGivenChargeWithoutRefunds() {
            //set up charge events for a successful payment journey that took place yesterday
            ZonedDateTime createdDate = ZonedDateTime.now().minusDays(1);
            ZonedDateTime enteringCardDetailsDate = createdDate.plusSeconds(1);
            ZonedDateTime authorisationReadyDate = enteringCardDetailsDate.plusSeconds(1);
            ZonedDateTime captureApprovedDate = authorisationReadyDate.plusSeconds(1);
            createEventsForSuccessfulPayment(chargeId, createdDate, enteringCardDetailsDate, authorisationReadyDate, captureApprovedDate);

            //verify that a get request returns the correct transaction events
            //each of the charge events added to the database has a distinct internal state but these map to just three external states
            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", gatewayAccountId, CHARGE_EXTERNAL_ID))
                    .then()
                    .statusCode(200)
                    .body("charge_id", is(CHARGE_EXTERNAL_ID))
                    .body("events.size()", equalTo(3))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdDate))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsDate))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedDate));
        }

        @Test
        public void shouldGetAllEventsForAGivenChargeWithRefunds() {
            //set up charge events for a successful payment journey that took place yesterday
            ZonedDateTime createdDate = ZonedDateTime.now().minusDays(1);
            ZonedDateTime enteringCardDetailsDate = createdDate.plusSeconds(1);
            ZonedDateTime authorisationReadyDate = enteringCardDetailsDate.plusSeconds(1);
            ZonedDateTime captureApprovedDate = authorisationReadyDate.plusSeconds(1);
            createEventsForSuccessfulPayment(chargeId, createdDate, enteringCardDetailsDate, authorisationReadyDate, captureApprovedDate);

            //set up a partial refund
            ZonedDateTime partialRefundCreatedDate = createdDate.plusMinutes(10);
            String gatewayTransactionIdForPartialRefund = RandomStringUtils.randomAlphanumeric(30);
            DatabaseFixtures.TestRefund partialRefund = createTestRefund(testCharge, partialRefundCreatedDate, gatewayTransactionIdForPartialRefund, 10L, SUBMITTED_BY);

            //set up the event history for the partial refund
            ZonedDateTime partialRefundCreatedEventDate = partialRefundCreatedDate.plus(1L, ChronoUnit.MILLIS);
            ZonedDateTime partialRefundSubmittedEventDate = partialRefundCreatedDate.plus(2L, ChronoUnit.MILLIS);
            ZonedDateTime partialRefundRefundedEventDate = partialRefundCreatedDate.plus(3L, ChronoUnit.MILLIS);
            createTestRefundHistory(partialRefund)
                    .insert(RefundStatus.CREATED, partialRefundCreatedEventDate, partialRefundSubmittedEventDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForPartialRefund, partialRefundSubmittedEventDate, partialRefundRefundedEventDate, SUBMITTED_BY, USER_EMAIL)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForPartialRefund, partialRefundRefundedEventDate, SUBMITTED_BY, testCharge.getExternalChargeId());

            //set up a second partial refund
            ZonedDateTime secondRefundCreatedDate = partialRefundCreatedDate.plusMinutes(1);
            String gatewayTransactionIdForSecondRefund = RandomStringUtils.randomAlphanumeric(10);
            DatabaseFixtures.TestRefund secondRefund = createTestRefund(testCharge, secondRefundCreatedDate, gatewayTransactionIdForSecondRefund, 90L, null);

            //set up the event history for the second refund
            ZonedDateTime secondRefundCreatedEventDate = secondRefundCreatedDate.plus(1L, ChronoUnit.MILLIS);
            ZonedDateTime secondRefundSubmittedEventDate = secondRefundCreatedDate.plus(2L, ChronoUnit.MILLIS);
            ZonedDateTime secondRefundRefundedEventDate = secondRefundCreatedDate.plus(3L, ChronoUnit.MILLIS);
            createTestRefundHistory(secondRefund)
                    .insert(RefundStatus.CREATED, secondRefundCreatedEventDate, secondRefundSubmittedEventDate)
                    .insert(RefundStatus.REFUND_SUBMITTED, gatewayTransactionIdForSecondRefund, secondRefundSubmittedEventDate, secondRefundRefundedEventDate)
                    .insert(RefundStatus.REFUNDED, gatewayTransactionIdForSecondRefund, secondRefundRefundedEventDate);

            //verify that a get request returns the correct transaction events
            //each of the charge events added to the database has a distinct internal state but these map to just three external states
            //each of the refund events added to the database has a distinct internal state but these map to just two external states for each refund
            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", gatewayAccountId, CHARGE_EXTERNAL_ID))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("charge_id", is(testCharge.getExternalChargeId()))
                    .body("events.size()", equalTo(7))
                    .body("events[0]", new TransactionEventMatcher("PAYMENT", withState("created", "false"), "100", createdDate))
                    .body("events[1]", new TransactionEventMatcher("PAYMENT", withState("started", "false"), "100", enteringCardDetailsDate))
                    .body("events[2]", new TransactionEventMatcher("PAYMENT", withState("success", "true"), "100", captureApprovedDate))
                    .body("events[3]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "10", partialRefundSubmittedEventDate, gatewayTransactionIdForPartialRefund, SUBMITTED_BY))
                    .body("events[4]", new TransactionEventMatcher("REFUND", withState("success", "true"), "10", partialRefundRefundedEventDate, gatewayTransactionIdForPartialRefund, SUBMITTED_BY))
                    .body("events[5]", new TransactionEventMatcher("REFUND", withState("submitted", "false"), "90", secondRefundSubmittedEventDate, gatewayTransactionIdForSecondRefund, null))
                    .body("events[6]", new TransactionEventMatcher("REFUND", withState("success", "true"), "90", secondRefundRefundedEventDate, gatewayTransactionIdForSecondRefund, null));
        }

        @Test
        public void shouldReturn404WhenAccountIdIsNonNumeric() {
            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", "invalid-account-id", CHARGE_EXTERNAL_ID))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("code", is(404))
                    .body("message", is("HTTP 404 Not Found"));
        }

        @Test
        public void shouldReturn404WhenChargeIdDoesNotExist() {
            app.givenSetup()
                    .get(format("/v1/api/accounts/%s/charges/%s/events", gatewayAccountId, "non-existent-charge"))
                    .then()
                    .contentType(JSON)
                    .statusCode(NOT_FOUND.getStatusCode())
                    .body("message", contains("Charge with id [non-existent-charge] not found."))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }
    }

    private void createEventsForSuccessfulPayment(long chargeId, ZonedDateTime createdDate, ZonedDateTime enteringCardDetailsDate, ZonedDateTime authorisationReadyDate, ZonedDateTime captureApprovedDate) {
        createTestChargeEvent(chargeId).withChargeStatus(CREATED).withDate(createdDate).insert();
        createTestChargeEvent(chargeId).withChargeStatus(ENTERING_CARD_DETAILS).withDate(enteringCardDetailsDate).insert();
        createTestChargeEvent(chargeId).withChargeStatus(AUTHORISATION_READY).withDate(authorisationReadyDate).insert();
        createTestChargeEvent(chargeId).withChargeStatus(CAPTURE_APPROVED).withDate(captureApprovedDate).insert();
        createTestChargeEvent(chargeId).withChargeStatus(CAPTURE_READY).withDate(captureApprovedDate.plusSeconds(1)).insert();
        createTestChargeEvent(chargeId).withChargeStatus(CAPTURE_SUBMITTED).withDate(captureApprovedDate.plusSeconds(2)).insert();
        createTestChargeEvent(chargeId).withChargeStatus(CAPTURED).withDate(captureApprovedDate.plusSeconds(3)).insert();
    }

    private DatabaseFixtures.TestChargeEvent createTestChargeEvent(long chargeId) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestChargeEvent()
                .withChargeId(chargeId);
    }

    private DatabaseFixtures.TestRefundHistory createTestRefundHistory(DatabaseFixtures.TestRefund testRefund) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestRefundHistory(testRefund);
    }
    
    private DatabaseFixtures.TestRefund createTestRefund(DatabaseFixtures.TestCharge testCharge, ZonedDateTime createdDate, String gatewayTransactionId, long amountRefunded, String submittedBy) {
        return withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestRefund()
                .withTestCharge(testCharge)
                .withAmount(amountRefunded)
                .withGatewayTransactionId(gatewayTransactionId)
                .withType(RefundStatus.REFUNDED)
                .withCreatedDate(createdDate)
                .withSubmittedBy(submittedBy)
                .withChargeExternalId(CHARGE_EXTERNAL_ID)
                .insert();
    }
}
