package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.EMAIL;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_LANGUAGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateLanguageIT {

    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");

    @Test
    void makeChargeWithNoExplicitLanguageDefaultsToEnglish() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL
        ));

        ValidatableResponse response = app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .body(JSON_LANGUAGE_KEY, is("en"))
                .contentType(JSON);

        String externalChargeId = response.extract().path(JSON_CHARGE_KEY);

        app.getConnectorRestApiClient()
                .withAccountId(app.getAccountId())
                .withChargeId(externalChargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_LANGUAGE_KEY, is("en"));
    }

    @Test
    void shouldReturn400WhenLanguageNotSupported() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_LANGUAGE_KEY, "not a supported language"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(Response.Status.BAD_REQUEST.getStatusCode());

    }

}
