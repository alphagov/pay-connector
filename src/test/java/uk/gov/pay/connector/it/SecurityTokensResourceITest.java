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
    private String accountId = "666";

    private String tokensUrlFor(String id) {
        return "/v1/frontend/tokens/" + id;
    }

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();

    @Before
    public void setupGatewayAccount() {
        app.getDatabaseTestHelper().addGatewayAccount(accountId, "test gateway");
    }

    @Test
    public void shouldSuccessfullyValidateToken() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        String tokenId = "tokenId";
        createNewCharge(chargeId, tokenId);

        givenSetup()
                .get(tokensUrlFor(tokenId))
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("chargeId", is(chargeId));
    }

    @Test
    public void shouldFailValidationWhenTokenNotFound() throws Exception {
        String tokenId = "tokenId";

        givenSetup()
                .get(tokensUrlFor(tokenId))
                .then()
                .statusCode(404)
                .contentType(JSON)
                .body("message", is("Token has expired!"));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() throws Exception {
        String chargeId = ((Integer) RandomUtils.nextInt(99999999)).toString();
        String tokenId = "tokenId";
        createNewCharge(chargeId, tokenId);

        givenSetup()
                .delete(tokensUrlFor(tokenId))
                .then()
                .statusCode(204)
                .body(isEmptyOrNullString());
    }

    private String createNewCharge(String chargeId, String tokenId) {
        app.getDatabaseTestHelper().addCharge(chargeId, accountId, 500, CREATED, "return_url");
        app.getDatabaseTestHelper().addToken(chargeId, tokenId);
        return chargeId;
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }
}
