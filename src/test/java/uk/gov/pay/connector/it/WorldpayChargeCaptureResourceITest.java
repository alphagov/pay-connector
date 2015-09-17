package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayPassword;
import static uk.gov.pay.connector.utils.EnvironmentUtils.getWorldpayUser;

public class WorldpayChargeCaptureResourceITest extends ChargeCaptureResourceITestBase {

    public WorldpayChargeCaptureResourceITest() {
        super("worldpay");
    }

    @Before
    public void before() throws Exception {
        Assume.assumeTrue(worldPayEnvironmentInitialized());
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
        return isNotBlank(getWorldpayUser()) && isNotBlank(getWorldpayPassword());
    }
}
