package uk.gov.pay.connector.it.resources.worldpay;

import com.jayway.restassured.http.ContentType;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

public class WorldpayChargeCancelResourceITest extends ChargingITestBase {

    public WorldpayChargeCancelResourceITest() {
        super("worldpay");
    }

    @Test
    public void cancelCharge_inWorldpaySystem() {
        String chargeId = createNewCharge(ChargeStatus.AUTHORISATION_SUCCESS);

        worldpay.mockCancelSuccess();
        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);
    }
}
