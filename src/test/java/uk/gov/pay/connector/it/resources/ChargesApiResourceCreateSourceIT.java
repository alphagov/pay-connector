package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;

import javax.ws.rs.core.Response;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.AMOUNT;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.EMAIL;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_AMOUNT_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_CHARGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_DESCRIPTION_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_EMAIL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_MESSAGE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_REFERENCE_VALUE;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.JSON_RETURN_URL_KEY;
import static uk.gov.pay.connector.it.base.ChargingITestBaseExtension.RETURN_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.JsonEncoder.toJsonWithNulls;
import static uk.gov.service.payments.commons.model.Source.CARD_API;

public class ChargesApiResourceCreateSourceIT {

    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");

    private static final String JSON_SOURCE_KEY = "source";

    @Test
    public void shouldCreateChargeWithSource() {
        String postBody = toJson(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_SOURCE_KEY, CARD_API
        ));

        ValidatableResponse response = app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(Response.Status.CREATED.getStatusCode())
                .contentType(JSON);

        String chargeExternalId = response.extract().path(JSON_CHARGE_KEY);
        Map<String, Object> charge = app.getDatabaseTestHelper().getChargeByExternalId(chargeExternalId);
        assertThat(CARD_API.toString(), equalTo(charge.get("source")));
    }

    @Test
    public void shouldReturn400ForInvalidSourceValue() {

        String postBody = toJsonWithNulls(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_SOURCE_KEY, "invalid-source0key"
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, contains("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
    }

    @Test
    public void shouldReturn400ForInvalidSourceType() {

        String postBody = toJsonWithNulls(Map.of(
                JSON_AMOUNT_KEY, AMOUNT,
                JSON_REFERENCE_KEY, JSON_REFERENCE_VALUE,
                JSON_DESCRIPTION_KEY, JSON_DESCRIPTION_VALUE,
                JSON_RETURN_URL_KEY, RETURN_URL,
                JSON_EMAIL_KEY, EMAIL,
                JSON_SOURCE_KEY, true
        ));

        app.getConnectorRestApiClient()
                .postCreateCharge(postBody)
                .statusCode(400)
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, contains("Field [source] must be one of CARD_API, CARD_PAYMENT_LINK, CARD_AGENT_INITIATED_MOTO"));
    }

}
