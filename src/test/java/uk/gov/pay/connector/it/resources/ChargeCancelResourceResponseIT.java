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
import static uk.gov.pay.connector.it.base.AddChargeParameters.Builder.anAddChargeParameters;

public class ChargeCancelResourceResponseIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("worldpay", app.getLocalPort(), app.getDatabaseTestHelper());

    public static Stream<Arguments> statusCode204() {
        return Stream.of(
                Arguments.of(ChargeStatus.CREATED, NO_CONTENT.getStatusCode()),
                Arguments.of(ChargeStatus.ENTERING_CARD_DETAILS, NO_CONTENT.getStatusCode())
        );
    }

    @ParameterizedTest()
    @MethodSource("statusCode204")
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth(ChargeStatus status, int statuscode) {
        String chargeId = createNewInPastChargeWithStatus(status);
        testBaseExtension.cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, statuscode);

        List<String> events = app.getDatabaseTestHelper().getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return testBaseExtension.addCharge(anAddChargeParameters().withChargeStatus(status)
                        .withCreatedDate(Instant.now().minus(1, HOURS)).build());
    }
}
