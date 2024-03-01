package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateReturnUrlIT extends NewChargingITestBase {

    public ChargesApiResourceCreateReturnUrlIT() {
        super(PROVIDER_NAME);
    }
    
    @Test
    public void shouldReturn422WhenReturnUrlIsMissing() {

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Missing mandatory attribute: return_url"))
                .body("error_identifier", is(ErrorIdentifier.MISSING_MANDATORY_ATTRIBUTE.toString()));

    }

    @Test
    public void shouldReturn422WhenReturnUrlIsPresentAndAuthorisationModeMotoApi() {

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_EMAIL_KEY, EMAIL,
                JSON_RETURN_URL_KEY, "https://i.should.not.be.here.co.uk",
                JSON_AUTH_MODE_KEY, JSON_AUTH_MODE_MOTO_API
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Unexpected attribute: return_url"))
                .body("error_identifier", is(ErrorIdentifier.UNEXPECTED_ATTRIBUTE.toString()));

    }

    @Test
    public void shouldReturn422WhenReturnUrlIsEmptyString() {

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, "",
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Missing mandatory attribute: return_url"))
                .body("error_identifier", is(ErrorIdentifier.MISSING_MANDATORY_ATTRIBUTE.toString()));

    }

    @Test
    public void shouldReturn422WhenReturnUrlIsNotValid() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, "not.a.valid.url",
                JSON_EMAIL_KEY, EMAIL
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("Invalid attribute value: return_url. Must be a valid URL format"))
                .body("error_identifier", is(ErrorIdentifier.INVALID_ATTRIBUTE_VALUE.toString()));
    }

}
