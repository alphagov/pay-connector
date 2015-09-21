package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.hamcrest.core.Is.is;

public class WorldpayCardResourceITest extends CardResourceITestBase {

    private String validCardDetails = buildJsonCardDetailsFor("4444333322221111");

    public WorldpayCardResourceITest() {
        super("worldpay");
    }

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
    }

    @Test
    public void shouldAuthoriseChargeForValidCardDetails() throws Exception {

        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        assertFrontendChargeStatusIs(chargeId, "AUTHORISATION SUCCESS");
    }

    @Test
    public void shouldReturnErrorAndDoNotUpdateChargeStatusIfSomeCardDetailsHaveAlreadyBeenSubmitted() throws Exception {
        String chargeId = createNewCharge();

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(204);

        String originalStatus = "AUTHORISATION SUCCESS";
        assertFrontendChargeStatusIs(chargeId, originalStatus);

        givenSetup()
                .body(validCardDetails)
                .post(cardUrlFor(chargeId))
                .then()
                .statusCode(400)    // Maybe this could be 409?
                .contentType(APPLICATION_JSON)
                .body("message", is(format("Card already processed for charge with id %s.", chargeId)));

        assertFrontendChargeStatusIs(chargeId, originalStatus);
    }

    @Test
    public void shouldReturnNotAuthorisedForWorldpayErrorCard() throws Exception {
        String cardDetailsToReject = buildJsonCardDetailsFor("REFUSED", "4444333322221111");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsToReject, expectedErrorMessage, expectedChargeStatus);
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

    private boolean worldPayEnvironmentInitialized() {
        WorldpayConfig worldpayConfig = app.getConf().getWorldpayConfig();
        return isNotBlank(worldpayConfig.getUsername()) && isNotBlank(worldpayConfig.getPassword());
    }
}
