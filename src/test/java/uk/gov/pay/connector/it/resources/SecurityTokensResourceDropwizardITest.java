package uk.gov.pay.connector.it.resources;

import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.resources.SecurityTokensResource;
import uk.gov.pay.connector.rules.DropwizardAppWithPostgresRule;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class SecurityTokensResourceDropwizardITest extends ChargingITestBase {

    @Rule
    public DropwizardAppWithPostgresRule app = new DropwizardAppWithPostgresRule();
    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestToken defaultTestToken;


    public SecurityTokensResourceDropwizardITest() {
        super("sandbox");
    }

    private String tokensUrlFor(String id) {
        return SecurityTokensResource.CHARGE_TOKEN_PATH.replace("{chargeTokenId}", id);
    }

    @Before
    public void setupGatewayAccount() {

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .insert();

        this.defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();

        this.defaultTestToken = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withTestToken(defaultTestCharge)
                .insert();
    }

    @Test
    public void shouldSuccessfullyGetChargeForToken() throws Exception {
        ValidatableResponse tokenGetsStatusCode = findTokenGetsStatusCode(defaultTestToken.getSecureRedirectToken(), 200);
        tokenGetsStatusCode
                .body("externalId", is(defaultTestCharge.getExternalChargeId()))
                .body("status", is(CREATED.getValue()))
                .body("gatewayAccount.service_name", is(defaultTestAccount.getServiceName()));
    }

    @Test
    public void shouldReturn404WhenTokenNotFound() throws Exception {
        findTokenGetsStatusCode("non-existant-secure-redirect-token", 404)
                .body("message", is("Token invalid!"));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() throws Exception {
        givenSetup()
                .delete(tokensUrlFor(defaultTestToken.getSecureRedirectToken()))
                .then()
                .statusCode(204)
                .body(isEmptyOrNullString());
        findTokenGetsStatusCode(defaultTestToken.getSecureRedirectToken(), 404)
                .body("message", is("Token invalid!"));
    }

    private ValidatableResponse findTokenGetsStatusCode(String secureRedirectToken, int expectedStatusCode) {
        return givenSetup()
                .get(tokensUrlFor(secureRedirectToken) + "/charge")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON);
    }
}
