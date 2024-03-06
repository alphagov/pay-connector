package uk.gov.pay.connector.it.resources.epdq;

import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

public class EpdqChargeApiResourceIT extends NewChargingITestBase {
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
