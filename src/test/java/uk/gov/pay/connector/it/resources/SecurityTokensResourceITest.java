package uk.gov.pay.connector.it.resources;

import com.jayway.restassured.response.ValidatableResponse;
import com.jayway.restassured.specification.RequestSpecification;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.resources.SecurityTokensResource;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.apache.commons.lang.math.RandomUtils.nextInt;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class SecurityTokensResourceITest {

    private static final String TOKEN_ID = "tokenId";
    private static final String ACCOUNT_ID = "23476";
    private static final String CHARGE_ID = "charge827364";

    private String tokensUrlFor(String id) {
        return SecurityTokensResource.CHARGE_TOKEN_PATH.replace("{chargeTokenId}", id);
    }

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(ACCOUNT_ID, "test gateway");
    }

    @Test
    public void shouldSuccessfullyGetChargeForToken() throws Exception {
        createNewChargeAndToken(CHARGE_ID, TOKEN_ID);
        ValidatableResponse tokenGetsStatusCode = findTokenGetsStatusCode(200);
        tokenGetsStatusCode
                .body("externalId", is(CHARGE_ID))
                .body("status", is(CREATED.getValue()));
    }

    @Test
    public void shouldReturn404WhenTokenNotFound() throws Exception {
        findTokenGetsStatusCode(404)
                .body("message", is("Token invalid!"));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() throws Exception {
        createNewChargeAndToken(CHARGE_ID, TOKEN_ID);
        givenSetup()
                .delete(tokensUrlFor(TOKEN_ID))
                .then()
                .statusCode(204)
                .body(isEmptyOrNullString());
        findTokenGetsStatusCode(404)
                .body("message", is("Token invalid!"));
    }

    private ValidatableResponse findTokenGetsStatusCode(int expectedStatusCode) {
        return givenSetup()
                .get(tokensUrlFor(TOKEN_ID)+"/charge")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON);
    }

    private void createNewChargeAndToken(String chargeId, String tokenId) {
        long chargeInternalId = nextInt();
        app.getDatabaseTestHelper().addCharge(chargeInternalId, chargeId, ACCOUNT_ID, 500, CREATED, "return_url", null);
        app.getDatabaseTestHelper().addToken(chargeInternalId, tokenId);
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
