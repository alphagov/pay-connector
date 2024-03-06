package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargesApiResourceCreateMotoIT extends NewChargingITestBase {

    public ChargesApiResourceCreateMotoIT() {
        super(PROVIDER_NAME);
    }

    @Test
    public void shouldReturn422WhenMotoIsTrueIfAccountDoesNotAllowIt() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_MOTO_KEY, true
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(422)
                .contentType(JSON)
                .body("message", contains("MOTO payments are not enabled for this gateway account"))
                .body("error_identifier", is(ErrorIdentifier.MOTO_NOT_ALLOWED.toString()));
    }

    @Test
    public void shouldCreateMotoChargeIfAccountAllowsIt() {
        databaseTestHelper.allowMoto(Long.parseLong(accountId));

        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_MOTO_KEY, true
        ));

        connectorRestApiClient
                .postCreateCharge(postBody)
                .statusCode(201)
                .contentType(JSON)
                .body(JSON_MOTO_KEY, is(true));
    }

}
