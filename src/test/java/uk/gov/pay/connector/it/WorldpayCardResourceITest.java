package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.it.base.CardResourceITestBase;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
    public void shouldNotAuthorise_AWorldpayErrorCard() throws Exception {
        String cardDetailsRejectedByWorldpay = buildJsonCardDetailsFor("REFUSED", "4444333322221111");

        String expectedErrorMessage = "This transaction was declined.";
        String expectedChargeStatus = "AUTHORISATION REJECTED";
        shouldReturnErrorForCardDetailsWithMessage(cardDetailsRejectedByWorldpay, expectedErrorMessage, expectedChargeStatus);
    }

    @Test
    public void shouldCaptureCardPayment_IfChargeWasPreviouslyAuthorised() {
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
