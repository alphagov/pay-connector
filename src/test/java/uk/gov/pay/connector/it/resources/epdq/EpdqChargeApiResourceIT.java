package uk.gov.pay.connector.it.resources.epdq;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

public class EpdqChargeApiResourceIT  {
    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("epdq");

    @BeforeAll
    static void setUp() {
        app.setUpBase();
    }


    @Test
    public void getChargeRefundStatusShouldBeUnavailable() {
        String externalChargeId = app.createNewChargeWith(CAPTURED, "a-gateway-tx-id");

        app.getConnectorRestApiClient().withChargeId(externalChargeId).getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("unavailable"));
    }
}
