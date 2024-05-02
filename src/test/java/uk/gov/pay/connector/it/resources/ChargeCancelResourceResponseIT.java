package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

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
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app);

    public static Stream<Arguments> statusCode400() {
        return Stream.of(
                Arguments.of(ChargeStatus.AUTHORISATION_REJECTED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.AUTHORISATION_ERROR, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.CAPTURE_READY, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.CAPTURED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.CAPTURE_SUBMITTED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.CAPTURE_ERROR, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.EXPIRED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.EXPIRE_CANCEL_FAILED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.SYSTEM_CANCEL_ERROR, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.SYSTEM_CANCELLED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.USER_CANCEL_READY, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.USER_CANCELLED, BAD_REQUEST.getStatusCode()),
                Arguments.of(ChargeStatus.USER_CANCEL_ERROR, BAD_REQUEST.getStatusCode())
        );
    }

    @ParameterizedTest()
    @MethodSource("statusCode400")
    public void respondWith400_whenNotCancellableState(ChargeStatus status, int statusCode) {
        String chargeId = createNewInPastChargeWithStatus(status);
        String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
        testBaseExtension.getConnectorRestApiClient()
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(statusCode)
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    public static Stream<Arguments> statusCode204() {
        return Stream.of(
                Arguments.of(ChargeStatus.CREATED, NO_CONTENT.getStatusCode()),
                Arguments.of(ChargeStatus.ENTERING_CARD_DETAILS, NO_CONTENT.getStatusCode())
        );
    }

    @ParameterizedTest()
    @MethodSource("statusCode204")
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status, int statuscode) {
        String chargeId = testBaseExtension.addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelevant");
        testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, statuscode);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return testBaseExtension.addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelavant");
    }
}
