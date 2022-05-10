package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.config.AuthorisationConfig;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ExecutorServiceConfig;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.lang.reflect.Field;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.it.JsonRequestHelper.buildJsonAuthorisationDetailsFor;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class CardAuthorizeDelayedGatewayResponseIT extends ChargingITestBase {
    private String validCardDetails = buildJsonAuthorisationDetailsFor(VALID_SANDBOX_CARD_LIST[0], "visa");

    private static final String[] VALID_SANDBOX_CARD_LIST = new String[]{
            "4444333322221111",
            "4917610000000000003",
            "4242424242424242",
            "4000056655665556",
            "5105105105105100",
            "5200828282828210",
            "371449635398431",
            "3566002020360505",
            "6011000990139424",
            "36148900647913"};

    public CardAuthorizeDelayedGatewayResponseIT() {
        super("sandbox");
    }
    
    @Test
    public void shouldReturn202_WhenGatewayAuthorisationResponseIsDelayed() throws NoSuchFieldException, IllegalAccessException {
        AuthorisationConfig conf = testContext.getAuthorisationConfig();
        Field timeoutInSeconds = conf.getClass().getDeclaredField("asynchronousAuthTimeoutInSeconds");
        timeoutInSeconds.setAccessible(true);
        timeoutInSeconds.setInt(conf, 0);

        String chargeId = createNewChargeWithNoTransactionId(ENTERING_CARD_DETAILS);
        given().port(testContext.getPort())
                .contentType(JSON)
                .body(validCardDetails)
                .post(authoriseChargeUrlFor(chargeId))
                .then()
                .statusCode(202)
                .contentType(JSON)
                .body("message", contains(format("Authorisation for charge already in progress, %s", chargeId)))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }
}
