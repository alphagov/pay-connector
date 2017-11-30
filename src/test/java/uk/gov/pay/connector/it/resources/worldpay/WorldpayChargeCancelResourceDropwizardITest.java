package uk.gov.pay.connector.it.resources.worldpay;

import com.jayway.restassured.http.ContentType;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

public class WorldpayChargeCancelResourceDropwizardITest extends ChargingITestBase {

    public WorldpayChargeCancelResourceDropwizardITest() {
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
