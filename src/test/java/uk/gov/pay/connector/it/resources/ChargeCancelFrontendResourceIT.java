package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.Instant;
import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.HOURS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;

public class ChargeCancelFrontendResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app);
    
    private static final List<ChargeStatus> NON_USER_CANCELLABLE_STATUSES = ImmutableList.of(
            AUTHORISATION_REJECTED,
            AUTHORISATION_ERROR,
            CAPTURED,
            CAPTURE_SUBMITTED,
            CAPTURE_ERROR,
            CAPTURE_ERROR,
            EXPIRED,
            EXPIRE_CANCEL_READY,
            EXPIRE_CANCEL_FAILED,
            SYSTEM_CANCEL_READY,
            SYSTEM_CANCEL_ERROR,
            SYSTEM_CANCELLED,
            USER_CANCEL_ERROR,
            USER_CANCELLED
    );
    
    @Test
    void respondWith204WithNoLockingState_whenCancellationBeforeAuth() {

        String chargeId = testBaseExtension.addCharge(ENTERING_CARD_DETAILS, "ref", Instant.now().minus(1, HOURS), "irrelvant");
        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(ENTERING_CARD_DETAILS.getValue(), USER_CANCELLED.getValue()));
    }

    @Test
    void respondWith204WithLockingState_whenCancellationAfterAuth() {
        String chargeId = testBaseExtension.addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(1, HOURS), "transaction-id");
        app.getWorldpayMockClient().mockCancelSuccess();

        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, NO_CONTENT.getStatusCode());

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                USER_CANCEL_READY.getValue(),
                USER_CANCELLED.getValue()));
    }

    @Test
    void respondWith204_whenCancellationDuringAuthReady() {

        String chargeId = testBaseExtension.addCharge(AUTHORISATION_READY, "ref", Instant.now().minus(1, HOURS), "irrelvant");
        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(AUTHORISATION_READY.getValue(), USER_CANCELLED.getValue()));
    }

    @Test
    void respondWith202_whenCancelAlreadyInProgress() {
        String chargeId = testBaseExtension.addCharge(USER_CANCEL_READY, "ref", Instant.now().minus(1, HOURS), "irrelvant");
        String expectedMessage = "User Cancellation for charge already in progress, " + chargeId;
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(ACCEPTED.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void respondWith204WithLockingState_whenCancelFailsAfterAuth() {

        String gatewayTransactionId = "gatewayTransactionId";
        app.getWorldpayMockClient().mockCancelError();
        String chargeId = testBaseExtension.addCharge(AUTHORISATION_SUCCESS, "ref", Instant.now().minus(1, HOURS), gatewayTransactionId);

        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCEL_ERROR, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                USER_CANCEL_READY.getValue(),
                USER_CANCEL_ERROR.getValue()));
    }

    @Test
    void respondWith204With3DSRequiredState_whenCancellationBeforeAuth() {
        String chargeId = testBaseExtension.addCharge(AUTHORISATION_3DS_REQUIRED, "ref", Instant.now().minus(1, HOURS), "irrelvant");
        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(AUTHORISATION_3DS_REQUIRED.getValue(), USER_CANCELLED.getValue()));
    }
    
    private String userCancelChargeAndCheckApiStatus(String chargeId, ChargeStatus targetState, int httpStatusCode) {
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(httpStatusCode);
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", is("failed"))
                .body("state.message", is("Payment was cancelled by the user"))
                .body("state.code", is("P0030"));

        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(targetState.getValue()));
        return chargeId;
    }

    @Test
    void respondWith404_whenPaymentNotFound() {
        String unknownChargeId = "2344363244";
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(unknownChargeId)
                .withAccountId(testBaseExtension.getAccountId())
                .postFrontendChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", contains("Charge with id [" + unknownChargeId + "] not found."))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
    
    @Test
    void respondWith400_whenNotCancellableState() {
        NON_USER_CANCELLABLE_STATUSES
                .forEach(status -> {
                    String chargeId = testBaseExtension.addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelavant");
                    String incorrectStateMessage = "Charge not in correct state to be processed, " + chargeId;

                    testBaseExtension.getConnectorRestApiClient()
                            .withChargeId(chargeId)
                            .postFrontendChargeCancellation()
                            .statusCode(BAD_REQUEST.getStatusCode())
                            .and()
                            .contentType(JSON)
                            .body("message", contains(incorrectStateMessage))
                            .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));

                });
    }
}
