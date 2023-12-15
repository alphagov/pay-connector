package uk.gov.pay.connector.it.resources.epdq;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class EpdqChargeApiResourceIT extends ChargingITestBase {
    public EpdqChargeApiResourceIT() {
        super("epdq");
    }

    @Test
    public void getChargeRefundStatusShouldBeUnavailable() {
        String externalChargeId = createNewChargeWith(CAPTURED, "a-gateway-tx-id");

        connectorRestApiClient.withChargeId(externalChargeId).getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("unavailable"));
    }
}
