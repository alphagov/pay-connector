package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;
import uk.gov.pay.connector.util.RestAssuredClient;

import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.*;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.model.api.ExternalChargeStatus.EXT_IN_PROGRESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.resources.ApiPaths.OLD_GET_CHARGE_API_PATH;
import static uk.gov.pay.connector.resources.ApiPaths.OLD_CHARGES_API_PATH;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;
import static uk.gov.pay.connector.util.LinksAssert.assertNextUrlLink;
import static uk.gov.pay.connector.util.LinksAssert.assertSelfLink;
import static uk.gov.pay.connector.util.NumberMatcher.isNumber;

public class ChargesApiResourceITest {
    private static final String FRONTEND_CARD_DETAILS_URL = "/charge/";

    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
    private static final String JSON_CHARGE_KEY = "charge_id";
    private static final String JSON_STATUS_KEY = "status";
    private static final String JSON_MESSAGE_KEY = "message";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    private String accountId = "72332423443245";
    private String returnUrl = "http://service.url/success-page/";

    private RestAssuredClient restApiCall = new RestAssuredClient(app, accountId, OLD_CHARGES_API_PATH);
    private RestAssuredClient restApiGetCall = new RestAssuredClient(app, accountId, OLD_GET_CHARGE_API_PATH);

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    private long amount = 6234L;

    @Test
    public void makeChargeAndRetrieveAmount() throws Exception {
        String expectedReference = "Test reference";
        String expectedDescription = "Test description";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, amount,
                JSON_REFERENCE_KEY, expectedReference,
                JSON_DESCRIPTION_KEY, expectedDescription,
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));
        ValidatableResponse response = restApiCall
                .postCreateCharge(postBody)
                .statusCode(CREATED.getStatusCode())
                .body(JSON_CHARGE_KEY, is(notNullValue()))
                .body(JSON_AMOUNT_KEY, isNumber(amount))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_RETURN_URL_KEY, is(returnUrl))
                .contentType(JSON);

        String chargeId = response.extract().path(JSON_CHARGE_KEY);
        String documentLocation = expectedChargeLocationFor(chargeId);

        response.header("Location", is(documentLocation));
        assertSelfLink(response, documentLocation);
        assertNextUrlLink(response, cardDetailsLocationFor(chargeId));

        ValidatableResponse getChargeResponse = restApiGetCall
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_AMOUNT_KEY, isNumber(amount))
                .body(JSON_REFERENCE_KEY, is(expectedReference))
                .body(JSON_DESCRIPTION_KEY, is(expectedDescription))
                .body(JSON_STATUS_KEY, is(ChargeStatus.CREATED.getValue()))
                .body(JSON_RETURN_URL_KEY, is(returnUrl));

        assertSelfLink(getChargeResponse, documentLocation);
    }

    @Test
    public void shouldFilterChargeStatusToReturnInProgressIfInternalStatusIsAuthorised() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, amount, AUTHORISATION_SUCCESS, returnUrl, null);
        app.getDatabaseTestHelper().addToken(chargeId, "tokenId");

        restApiGetCall
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(OK.getStatusCode())
                .contentType(JSON)
                .body(JSON_CHARGE_KEY, is(chargeId))
                .body(JSON_STATUS_KEY, is(EXT_IN_PROGRESS.getValue()));
    }

    @Test
    public void cannotMakeChargeForMissingGatewayAccount() throws Exception {
        String missingGatewayAccount = "1234123";
        String postBody = toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, amount,
                JSON_DESCRIPTION_KEY, "Test description",
                JSON_GATEWAY_ACC_KEY, missingGatewayAccount,
                JSON_RETURN_URL_KEY, returnUrl));
        restApiCall.postCreateCharge(postBody)
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Unknown gateway account: " + missingGatewayAccount));
    }

    @Test
    public void cannotMakeChargeForMissingFields() throws Exception {
        restApiCall.postCreateCharge("{}")
                .statusCode(BAD_REQUEST.getStatusCode())
                .contentType(JSON)
                .header("Location", is(nullValue()))
                .body(JSON_CHARGE_KEY, is(nullValue()))
                .body(JSON_MESSAGE_KEY, is("Field(s) missing: [amount, description, gateway_account_id, return_url]"));
    }

    @Test
    public void cannotGetCharge_WhenInvalidChargeId() throws Exception {
        String chargeId = "23235124";
        restApiGetCall
                .withChargeId(chargeId)
                .getCharge()
                .statusCode(NOT_FOUND.getStatusCode())
                .contentType(JSON)
                .body(JSON_MESSAGE_KEY, is(format("Charge with id [%s] not found.", chargeId)));
    }

    private String expectedChargeLocationFor(String chargeId) {
        return "http://localhost:" + app.getLocalPort() + OLD_GET_CHARGE_API_PATH.replace("{chargeId}", chargeId);
    }

    private String cardDetailsLocationFor(String chargeId) {
        String chargeTokenId = app.getDatabaseTestHelper().getChargeTokenId(chargeId);
        return "http://Frontend" + FRONTEND_CARD_DETAILS_URL + chargeId + "?chargeTokenId=" + chargeTokenId;
    }
}
