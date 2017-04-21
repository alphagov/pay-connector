package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingWithSyncCaptureITestBase;

import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;

public class WorldpayCardWithSyncCaptureResourceITest extends ChargingWithSyncCaptureITestBase{

    public WorldpayCardWithSyncCaptureResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldCaptureCardPaymentImmediately_IfAsynchronousFeatureFlagIsOff() {
        String chargeId = authoriseNewCharge();

        worldpay.mockCaptureSuccess();

        givenSetup()
                .post(captureChargeUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
        assertApiStateIs(chargeId, EXTERNAL_SUCCESS.getStatus());
    }
}
