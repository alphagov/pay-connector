package uk.gov.pay.connector.it.resources.epdq;

import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;

public class EpdqChargeApiResourceITest extends ChargingITestBase {
    public EpdqChargeApiResourceITest() {
        super("epdq");
    }

    @Test
    public void shouldSuccessfully_authorise3ds() {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        String chargeId = externalChargeId.replace("charge-", "");
        String expectedHtml = "someHtml";
        app.getDatabaseTestHelper().updateCharge3dsDetails(Long.valueOf(chargeId), null, null, expectedHtml);

        connectorRestApiClient.withChargeId(externalChargeId).getFrontendCharge()
                .statusCode(200)
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .body("auth_3ds_data.htmlOut", is(expectedHtml));
    }
}
