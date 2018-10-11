package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.RestAssuredClient;

import java.time.ZonedDateTime;
import java.util.List;

import static com.jayway.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeCancelFrontendResourceITest extends ChargingITestBase {

    private RestAssuredClient connectorRestApi;
    private RestAssuredClient restFrontendCall;


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

    public ChargeCancelFrontendResourceITest() {
        super("worldpay");
    }

    @Before
    public void setupGatewayAccount() {
        connectorRestApi = new RestAssuredClient(app.getLocalPort(), accountId);
        restFrontendCall = new RestAssuredClient(app.getLocalPort(), accountId);
    }

    @Test
    public void respondWith204WithNoLockingState_whenCancellationBeforeAuth() {

        String chargeId = addCharge(ENTERING_CARD_DETAILS, "ref", ZonedDateTime.now().minusHours(1), "irrelvant");
        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(ENTERING_CARD_DETAILS.getValue(), USER_CANCELLED.getValue()));
    }

    @Test
    public void respondWith204WithLockingState_whenCancellationAfterAuth() {
        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");
        worldpay.mockCancelSuccess();

        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, NO_CONTENT.getStatusCode());

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                USER_CANCEL_READY.getValue(),
                USER_CANCELLED.getValue()));
    }

    @Test
    public void respondWith202_whenCancelAlreadyInProgress() {
        String chargeId = addCharge(USER_CANCEL_READY, "ref", ZonedDateTime.now().minusHours(1), "irrelvant");
        String expectedMessage = "User Cancellation for charge already in progress, " + chargeId;
        connectorRestApi
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(ACCEPTED.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is(expectedMessage));
    }

    @Test
    public void respondWith204WithLockingState_whenCancelFailsAfterAuth() {

        String gatewayTransactionId = "gatewayTransactionId";
        worldpay.mockCancelError();
        String chargeId = addCharge(AUTHORISATION_SUCCESS, "ref", ZonedDateTime.now().minusHours(1), gatewayTransactionId);

        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCEL_ERROR, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(3));
        assertThat(events, hasItems(AUTHORISATION_SUCCESS.getValue(),
                USER_CANCEL_READY.getValue(),
                USER_CANCEL_ERROR.getValue()));
    }

    @Test
    public void respondWith204With3DSRequeirdState_whenCancellationBeforeAuth() {

        String chargeId = addCharge(AUTHORISATION_3DS_REQUIRED, "ref", ZonedDateTime.now().minusHours(1), "irrelvant");
        userCancelChargeAndCheckApiStatus(chargeId, USER_CANCELLED, 204);
        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(AUTHORISATION_3DS_REQUIRED.getValue(), USER_CANCELLED.getValue()));
    }

    @Test
    public void respondWith409_whenCancellationDuringAuth3DSReady() {
        String chargeId = addCharge(AUTHORISATION_3DS_READY, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");
        worldpay.mockCancelSuccess();

        connectorRestApi
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(409);

        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", is("started"));
    }

    private String userCancelChargeAndCheckApiStatus(String chargeId, ChargeStatus targetState, int httpStatusCode) {
        connectorRestApi
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(httpStatusCode);
        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", is("failed"))
                .body("state.message", is("Payment was cancelled by the user"))
                .body("state.code", is("P0030"));

        restFrontendCall
                .withChargeId(chargeId)
                .getFrontendCharge()
                .body("status", is(targetState.getValue()));
        return chargeId;
    }

    @Test
    public void respondWith404_whenPaymentNotFound() {
        String unknownChargeId = "2344363244";
        connectorRestApi
                .withChargeId(unknownChargeId)
                .withAccountId(accountId)
                .postFrontendChargeCancellation()
                .statusCode(NOT_FOUND.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", is("Charge with id [" + unknownChargeId + "] not found."));
    }

    @Test
    public void respondWith409_whenCancellationDuringAuthReady() {
        String chargeId = addCharge(AUTHORISATION_READY, "ref", ZonedDateTime.now().minusHours(1), "transaction-id");
        worldpay.mockCancelSuccess();

        connectorRestApi
                .withChargeId(chargeId)
                .postFrontendChargeCancellation()
                .statusCode(409);

        connectorRestApi
                .withChargeId(chargeId)
                .getCharge()
                .body("state.status", is("started"));
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        NON_USER_CANCELLABLE_STATUSES
                .forEach(status -> {
                    String chargeId = addCharge(status, "ref", ZonedDateTime.now().minusHours(1), "irrelavant");
                    String incorrectStateMessage = "Charge not in correct state to be processed, " + chargeId;

                    connectorRestApi
                            .withChargeId(chargeId)
                            .postFrontendChargeCancellation()
                            .statusCode(BAD_REQUEST.getStatusCode())
                            .and()
                            .contentType(JSON)
                            .body("message", is(incorrectStateMessage));

                });
    }
}
