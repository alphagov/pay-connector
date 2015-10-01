package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

public class WorldpayChargeCancelResourceITest extends CardResourceITestBase {

    private String validCardDetails = buildJsonCardDetailsFor("4444333322221111");

    public WorldpayChargeCancelResourceITest() {
        super("worldpay");
    }

    @Test
    public void cancelCharge_inWorldpaySystem() {
        String chargeId = createAndAuthoriseCharge(validCardDetails);
        givenSetup()
                .post(cancelChargePath(chargeId))
                .then()
                .statusCode(204);
    }
}
