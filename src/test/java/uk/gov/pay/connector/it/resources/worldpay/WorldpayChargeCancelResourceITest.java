package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

public class WorldpayChargeCancelResourceITest extends CardResourceITestBase {

    public WorldpayChargeCancelResourceITest() {
        super("worldpay");
    }

    @Test
    public void cancelCharge_inWorldpaySystem() {
        String gatewayTransactionId = "irrelevant";
        String chargeId = createNewChargeWith(ChargeStatus.AUTHORISATION_SUCCESS, gatewayTransactionId );

        worldpay.mockCancelResponse(gatewayTransactionId);
        givenSetup()
                .post(cancelChargeUrlFor(chargeId))
                .then()
                .statusCode(204);
    }
}
