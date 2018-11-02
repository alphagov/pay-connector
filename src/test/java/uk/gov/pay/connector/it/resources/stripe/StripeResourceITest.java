package uk.gov.pay.connector.it.resources.stripe;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeResourceITest extends ChargingITestBase {

    private String validAuthorisationDetails = buildJsonAuthorisationDetailsFor("4444333322221111", "visa");
    
    public StripeResourceITest() {
        super(PaymentGatewayName.STRIPE.getName());
    }
    
    @Test
    public void authoriseCharge() {
        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        
        stripeMockClient.mockCreateSource();
        stripeMockClient.mockCreateCharge();

        givenSetup()
                .body(validAuthorisationDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .body("status", Matchers.is(AUTHORISATION_SUCCESS.toString()))
                .statusCode(200);
    }
}
