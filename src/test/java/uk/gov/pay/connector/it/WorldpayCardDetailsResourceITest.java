package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayPassword;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayUser;

public class WorldpayCardDetailsResourceITest extends CardDetailsResourceITestBase {

    private String validCardDetails = buildJsonCardDetailsFor("4444333322221111");

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
    }

    public WorldpayCardDetailsResourceITest() {
        super("worldpay");
    }

    @Test
    public void shouldAuthoriseChargeForValidCardDetails() throws Exception {

        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertChargeStatusIs(chargeId, "AUTHORISATION SUCCESS");
    }

    private boolean worldPayEnvironmentInitialized() {
        return isNotBlank(getWorldpayUser()) && isNotBlank(getWorldpayPassword());
    }

    //    @Test

//
//    public void shouldReturnErrorAndDoNotUpdateChargeStatusIfSomeCardDetailsHaveAlreadyBeenSubmitted() throws Exception {
//        String chargeId = createNewCharge();
//
//        givenSetup()
//                .body(validCardDetails)
//                .post(cardUrlFor(chargeId))
//                .then()
//                .statusCode(204);
//
//        String originalStatus = "AUTHORISATION SUCCESS";
//        assertChargeStatusIs(chargeId, originalStatus);
//
//        givenSetup()
//                .body(validCardDetails)
//                .post(cardUrlFor(chargeId))
//                .then()
//                .statusCode(400)
//                .contentType(JSON)
//                .body("message", is(format("Card already processed for charge with id %s.", chargeId)));
//
//        assertChargeStatusIs(chargeId, originalStatus);
//    }

    @Test
    public void shouldReturnNotAuthorisedForWorldpayErrorCard() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("REFUSED", "4444333322221111");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
    }


}
