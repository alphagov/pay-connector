package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;

import static io.restassured.http.ContentType.JSON;
import static java.time.temporal.ChronoUnit.HOURS;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class ChargeCancelResource400ResponseParameterizedIT extends NewChargingITestBase {

    @Parameterized.Parameter()
    public ChargeStatus notCancellableState;

    public ChargeCancelResource400ResponseParameterizedIT() {
        super("worldpay");
    }

    @Parameterized.Parameters()
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {ChargeStatus.AUTHORISATION_REJECTED},
                {ChargeStatus.AUTHORISATION_ERROR},
                {ChargeStatus.CAPTURE_READY},
                {ChargeStatus.CAPTURED},
                {ChargeStatus.CAPTURE_SUBMITTED},
                {ChargeStatus.CAPTURE_ERROR},
                {ChargeStatus.EXPIRED},
                {ChargeStatus.EXPIRE_CANCEL_FAILED},
                {ChargeStatus.SYSTEM_CANCEL_ERROR},
                {ChargeStatus.SYSTEM_CANCELLED},
                {ChargeStatus.USER_CANCEL_READY},
                {ChargeStatus.USER_CANCELLED},
                {ChargeStatus.USER_CANCEL_ERROR}
        });
    }

    @Test
    public void respondWith400_whenNotCancellableState() {
        String chargeId = createNewInPastChargeWithStatus(notCancellableState);
        String expectedMessage = "Charge not in correct state to be processed, " + chargeId;
        connectorRestApiClient
                .withChargeId(chargeId)
                .postChargeCancellation()
                .statusCode(BAD_REQUEST.getStatusCode())
                .and()
                .contentType(JSON)
                .body("message", contains(expectedMessage))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private String createNewInPastChargeWithStatus(ChargeStatus status) {
        return addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelavant");
    }
}
