package uk.gov.pay.connector.it;

import com.jayway.restassured.specification.RequestSpecification;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.util.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static uk.gov.pay.connector.model.ChargeStatus.CREATED;

public class SecurityTokensResourceITest {
    private static final String TOKEN_ID = "tokenId";
    private static final String ACCOUNT_ID = "23476";
    private static final String CHARGE_ID = "827364";

    private String tokensUrlFor(String id) {
        return "/v1/frontend/tokens/" + id;
    }

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(ACCOUNT_ID, "test gateway");
    }

    @Test
    public void shouldSuccessfullyValidateToken() throws Exception {
        createNewCharge(CHARGE_ID, TOKEN_ID);
        givenSetup()
                .get(tokensUrlFor(TOKEN_ID))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("chargeId", is(CHARGE_ID));
    }

    @Test
    public void shouldFailValidationWhenTokenNotFound() throws Exception {
        givenSetup()
                .get(tokensUrlFor(TOKEN_ID))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is("Token has expired!"));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() throws Exception {
        createNewCharge(CHARGE_ID, TOKEN_ID);
        givenSetup()
                .delete(tokensUrlFor(TOKEN_ID))
                .then()
                .statusCode(204)
                .body(isEmptyOrNullString());
    }

    private String createNewCharge(String chargeId, String tokenId) {
        app.getDatabaseTestHelper().addCharge(chargeId, ACCOUNT_ID, 500, CREATED, "return_url");
        app.getDatabaseTestHelper().addToken(chargeId, tokenId);
        return chargeId;
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
