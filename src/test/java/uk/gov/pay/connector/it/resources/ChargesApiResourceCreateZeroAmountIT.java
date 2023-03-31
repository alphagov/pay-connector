package uk.gov.pay.connector.it.resources;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
@Ignore
public class ChargesApiResourceCreateZeroAmountIT extends ChargingITestBase {

    public ChargesApiResourceCreateZeroAmountIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn422WhenAmountIsZeroIfAccountDoesNotAllowIt() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, 0,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Zero amount charges are not enabled for this gateway account"))
                .body("error_identifier", is(ErrorIdentifier.ZERO_AMOUNT_NOT_ALLOWED.toString()));
    }

    @Test
    public void shouldMakeChargeWhenAmountIsZeroIfAccountAllowsIt() {
        databaseTestHelper.allowZeroAmount(Long.parseLong(accountId));

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, 0,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(201)
                .contentType(JSON)
                .body("amount", is(0));
    }

}
