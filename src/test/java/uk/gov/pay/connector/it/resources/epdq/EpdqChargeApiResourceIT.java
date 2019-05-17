package uk.gov.pay.connector.it.resources.epdq;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class EpdqChargeApiResourceIT extends ChargingITestBase {
    public EpdqChargeApiResourceIT() {
        super("epdq");
    }

    @Test
    public void shouldSuccessfully_authorise3ds() {
        String externalChargeId = createNewChargeWithNoTransactionId(AUTHORISATION_3DS_REQUIRED);
        String chargeId = externalChargeId.replace("charge-", "");
        String expectedHtml = "someHtml";
        databaseTestHelper.updateCharge3dsDetails(Long.valueOf(chargeId), null, null, expectedHtml);

        connectorRestApiClient.withChargeId(externalChargeId).getFrontendCharge()
                .statusCode(200)
                .body("status", is(AUTHORISATION_3DS_REQUIRED.toString()))
                .body("auth_3ds_data.htmlOut", is(expectedHtml));
    }
}
