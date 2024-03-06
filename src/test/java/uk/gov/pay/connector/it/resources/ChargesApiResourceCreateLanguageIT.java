package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateLanguageIT extends NewChargingITestBase {

    public ChargesApiResourceCreateLanguageIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void makeChargeWithNoExplicitLanguageDefaultsToEnglish() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        ValidatableResponse response = connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body(JSON_LANGUAGE_KEY, is("en"))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);

        connectorRestApiClient
                .withAccountId(accountId)
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_LANGUAGE_KEY, is("en"));
    }

    @Test
    public void shouldReturn400WhenLanguageNotSupported() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_LANGUAGE_KEY, "not a supported language"
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

    }

}
