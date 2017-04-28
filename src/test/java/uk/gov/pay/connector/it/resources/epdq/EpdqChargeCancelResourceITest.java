package uk.gov.pay.connector.it.resources.epdq;

import com.jayway.restassured.http.ContentType;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.model.domain.ChargeStatus;

public class EpdqChargeCancelResourceITest extends ChargingITestBase {

    public EpdqChargeCancelResourceITest() {
        super("epdq");
    }

    @Test
    public void shouldCancelACharge() {
        String chargeId = createNewCharge(ChargeStatus.AUTHORISATION_SUCCESS);

//        epdq.mockCancelSuccess();
        givenSetup()
                .contentType(ContentType.JSON)
                .post(cancelChargeUrlFor(accountId, chargeId))
                .then()
                .statusCode(204);
    }
}
