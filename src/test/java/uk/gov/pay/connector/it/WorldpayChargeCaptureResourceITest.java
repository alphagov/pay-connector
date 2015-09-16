package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayPassword;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayUser;

public class WorldpayChargeCaptureResourceITest extends ChargeCaptureResourceITestBase {

    public WorldpayChargeCaptureResourceITest() {
        super("worldpay");
    }

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
    }

    @Test
    public void shouldConfirmCardPaymentIfChargeWasAuthorised() {
        String chargeId = authoriseNewCharge();

        givenSetup()
                .post(chargeCaptureUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "CAPTURED");
        assertApiStatusIs(chargeId, "SUCCEEDED");
    }

//    @Test
//    public void shouldReturnErrorWithoutChangingChargeState_IfOriginalStateIsNotAuthSuccess() {
//        String chargeIdNotAuthorised = createNewChargeWithStatus(AUTHORISATION_SUBMITTED);
//
//        givenSetup()
//                .post(chargeCaptureUrlFor(chargeIdNotAuthorised))
//                .then()
//                .statusCode(400)
//                .contentType(JSON)
//                .body("message", is("Cannot capture a charge with status " + AUTHORISATION_SUBMITTED.getValue() + "."));
//
//        assertFrontendChargeStatusIs(chargeIdNotAuthorised, "AUTHORISATION SUBMITTED");
//    }
//
//    @Test
//    public void shouldReturn404iftheChargeCannotBeFound() {
//        String unknownChargeId = "398579438759438";
//
//        givenSetup()
//                .post(chargeCaptureUrlFor(unknownChargeId))
//                .then()
//                .statusCode(404);
//    }

    private boolean worldPayEnvironmentInitialized() {
        return isNotBlank(getWorldpayUser()) && isNotBlank(getWorldpayPassword());
    }
}
