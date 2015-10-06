package uk.gov.pay.connector.it.resources.smartpay;

import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

public class SmartpayCardResourceITest extends CardResourceITestBase {

    private String validCardDetails = buildCardDetailsWith("737");

    public SmartpayCardResourceITest() {
        super("smartpay");
    }

    @Test
    public void shouldAuthoriseCharge_ForValidCardDetails() throws Exception {

        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "AUTHORISATION SUCCESS");
    }

    @Test
    public void shouldNotAuthorise_ASmartpayErrorCard() throws Exception {
        String cardWithWrongCVC = buildCardDetailsWith("999");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardWithWrongCVC, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldCancelCharge() {
        String chargeId = createAndAuthoriseCharge(validCardDetails);
        givenSetup()
                .post(cancelChargePath(chargeId))
                .then()
                .statusCode(204);
    }

    private String buildCardDetailsWith(String cvc) {

        return buildJsonCardDetailsFor(
                "Mr.Payment",
                "5555444433331111",
                cvc,
                "08/18",
                "line 2",
                "line 3",
                "London",
                null);
    }
}