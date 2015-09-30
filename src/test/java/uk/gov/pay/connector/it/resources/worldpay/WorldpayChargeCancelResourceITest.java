package uk.gov.pay.connector.it.resources.worldpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

public class WorldpayChargeCancelResourceITest extends CardResourceITestBase {

    private String validCardDetails = buildJsonCardDetailsFor("4444333322221111");

    public WorldpayChargeCancelResourceITest() {
        super("worldpay");
    }

    private String cancelChargePath(String chargeId) {
        return "/v1/api/charges/" + chargeId + "/cancel";
    }

    @Test
    public void cancelCharge_inWorldpaySystem() {
        String chargeId = createAndAuthoriseCharge();

        givenSetup()
                .post(cancelChargePath(chargeId))
                .then()
                .statusCode(204);
    }

    @Test
    public void cancelCharge_WorldpayRefuses() {
        String chargeId = createAndAuthoriseCharge();
    }

    private String createAndAuthoriseCharge() {
        String chargeId = createNewCharge();
        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);
        return chargeId;
    }
}
