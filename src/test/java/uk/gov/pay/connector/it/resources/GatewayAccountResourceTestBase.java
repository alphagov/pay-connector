package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceTestBase {

    public static final String ACCOUNTS_API_URL = "/v1/api/accounts/";
    public static final String ACCOUNTS_FRONTEND_URL = "/v1/frontend/accounts/";

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    protected RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

    protected String createAGatewayAccountFor(String testProvider) {
        ValidatableResponse response = givenSetup()
                .body(toJson(ImmutableMap.of("payment_provider", testProvider)))
                .post(ACCOUNTS_API_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, testProvider);

        assertGatewayAccountCredentialsAreEmptyInDB(response);

        return response.extract().path("gateway_account_id");
    }

    protected void assertGettingAccountReturnsProviderName(ValidatableResponse response, String providerName) {
        givenSetup()
                .get(response.extract().header("Location"))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is(providerName))
                .body("gateway_account_id", is(notNullValue()));
    }

    protected void assertCorrectAccountLocationIn(ValidatableResponse response) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }

    private void assertGatewayAccountCredentialsAreEmptyInDB(ValidatableResponse response) {
        String gateway_account_id = response.extract().path("gateway_account_id");
        JsonObject accountCredentials = app.getDatabaseTestHelper().getAccountCredentials(gateway_account_id);
        assertThat(accountCredentials, is(new JsonObject()));
    }

}
