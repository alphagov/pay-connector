package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.HOURS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

public class ChargeCancelResourceResponseIT {
    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("worldpay");

    @BeforeAll
    public static void setUp() {
        app.setUpBase();
    }

    public static Collection<Object[]> statusCode400() {
        return Arrays.asList(new Object[][]{
                {ChargeStatus.AUTHORISATION_REJECTED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.AUTHORISATION_ERROR, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.CAPTURE_READY, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.CAPTURED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.CAPTURE_SUBMITTED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.CAPTURE_ERROR, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.EXPIRED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.EXPIRE_CANCEL_FAILED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.SYSTEM_CANCEL_ERROR, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.SYSTEM_CANCELLED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.USER_CANCEL_READY, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.USER_CANCELLED, BAD_REQUEST.getStatusCode()},
                {ChargeStatus.USER_CANCEL_ERROR, BAD_REQUEST.getStatusCode()}
        });
    }

    @ParameterizedTest()
    @MethodSource("statusCode400")
    public void respondWith400_whenNotCancellableState(ChargeStatus status, int statusCode) {
        String chargeId = createNewInPastChargeWithStatus(status);
        String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
        app.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(statusCode)
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    public static Collection<Object[]> statusCode204() {
        return Arrays.asList(new Object[][]{
                { ChargeStatus.CREATED, NO_CONTENT.getStatusCode() }, { ChargeStatus.ENTERING_CARD_DETAILS, NO_CONTENT.getStatusCode() }
        });
    }

    @ParameterizedTest()
    @MethodSource("statusCode204")
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status, int statuscode) {
        String chargeId = app.addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelevant");
        app.cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, statuscode);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return app.addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelavant");
    }
}
