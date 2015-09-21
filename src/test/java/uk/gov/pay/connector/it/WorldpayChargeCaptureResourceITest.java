package uk.gov.pay.connector.it;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import uk.gov.pay.connector.app.WorldpayConfig;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
        WorldpayConfig worldpayConfig = app.getConf().getWorldpayConfig();
        return isNotBlank(worldpayConfig.getUsername()) && isNotBlank(worldpayConfig.getPassword());
    }
}
