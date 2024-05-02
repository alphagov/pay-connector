package uk.gov.pay.connector.it.resources.epdq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.base.ITestBaseExtension;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;

public class EpdqChargeApiResourceIT  {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    public static ITestBaseExtension testBaseExtension = new ITestBaseExtension("epdq", app.getLocalPort(), app.getDatabaseTestHelper());

    @Test
    public void getChargeRefundStatusShouldBeUnavailable() {
        String externalChargeId = testBaseExtension.createNewChargeWith(CAPTURED, "a-gateway-tx-id");

        testBaseExtension.getConnectorRestApiClient().withChargeId(externalChargeId).getCharge()
                .statusCode(200)
                .body("refund_summary.status", is("unavailable"));
    }
}
