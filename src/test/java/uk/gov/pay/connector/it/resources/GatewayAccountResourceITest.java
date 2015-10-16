package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.Matchers.*;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceITest {

    public static final String ACCOUNT_URL = "/v1/api/accounts/";
    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Test
    public void getAccountShouldReturn404IfAccountIdIsUnknown() throws Exception {

        String unknownAcocuntId = "92348739";

        givenSetup()
                .get(ACCOUNT_URL + unknownAcocuntId)
                .then()
                .statusCode(404);
    }

    @Test
    public void createGatewayAccountWithoutPaymentProviderDefaultsToSandbox() throws Exception {

        String payload = toJson(ImmutableMap.of("name", "test account"));

        ValidatableResponse response = givenSetup()
                .body(payload)
                .post(ACCOUNT_URL)
                .then()
                .statusCode(201);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, "sandbox");
    }

    @Test
    public void createAccountShouldFailIfPaymentProviderIsNotSandboxOfWorldpay() throws Exception {
        String testProvider = "random";
        String payload = toJson(ImmutableMap.of("payment_provider", testProvider));

        givenSetup()
                .body(payload)
                .post(ACCOUNT_URL)
                .then()
                .statusCode(400)
                .contentType(JSON)
                .body("message", is(format("Unsupported payment provider %s.", testProvider)));
    }

    @Test
    public void createAGatewayAccountForSandbox() throws Exception {

        createAGatewayAccountFor("sandbox");
    }

    @Test
    public void createAGatewayAccountForWorldpay() throws Exception {

        createAGatewayAccountFor("worldpay");
    }

    @Test
    public void createAGatewayAccountForSmartpay() throws Exception {

        createAGatewayAccountFor("smartpay");
    }

    private void createAGatewayAccountFor(String testProvider) {
        ValidatableResponse response = givenSetup()
                .body(toJson(ImmutableMap.of("payment_provider", testProvider)))
                .post(ACCOUNT_URL)
                .then()
                .statusCode(201)
                .contentType(JSON);

        assertCorrectAccountLocationIn(response);

        assertGettingAccountReturnsProviderName(response, testProvider);
    }

    private void assertGettingAccountReturnsProviderName(ValidatableResponse response, String providerName) {
        givenSetup()
                .get(response.extract().header("Location"))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("payment_provider", is(providerName))
                .body("gateway_account_id", is(notNullValue()));
    }

    private void assertCorrectAccountLocationIn(ValidatableResponse response) {
        String accountId = response.extract().path("gateway_account_id");
        String urlSlug = "api/accounts/" + accountId;

        response.header("Location", containsString(urlSlug))
                .body("gateway_account_id", containsString(accountId))
                .body("links[0].href", containsString(urlSlug))
                .body("links[0].rel", is("self"))
                .body("links[0].method", is("GET"));
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
