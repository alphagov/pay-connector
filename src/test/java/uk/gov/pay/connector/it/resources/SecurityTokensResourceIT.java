package uk.gov.pay.connector.it.resources;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.junit.DropwizardTestContext;
import uk.gov.pay.connector.junit.TestContext;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class SecurityTokensResourceIT {

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    private DatabaseFixtures.TestToken defaultTestToken;

    @DropwizardTestContext
    private TestContext testContext;

    private DatabaseTestHelper databaseTestHelper;

    @Before
    public void setup() {
        databaseTestHelper = testContext.getDatabaseTestHelper();

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();

        defaultTestToken = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestToken()
                .withTestToken(defaultTestCharge)
                .insert();
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    private String tokensUrlFor(String id) {
        return "/v1/frontend/tokens/{chargeTokenId}".replace("{chargeTokenId}", id);//format("/v1/frontend/tokens/%s", id);
    }

    @Test
    public void shouldSuccessfullyGetChargeForToken() {
        ValidatableResponse tokenGetsStatusCode = findTokenGetsStatusCode(defaultTestToken.getSecureRedirectToken(), 200);
        tokenGetsStatusCode
                .body("externalId", is(defaultTestCharge.getExternalChargeId()))
                .body("status", is(CREATED.getValue()))
                .body("gatewayAccount.service_name", is(defaultTestAccount.getServiceName()));
    }

    @Test
    public void shouldReturn404WhenTokenNotFound() {
        findTokenGetsStatusCode("non-existant-secure-redirect-token", 404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() {
        givenSetup()
                .delete(tokensUrlFor(defaultTestToken.getSecureRedirectToken()))
                .then()
                .statusCode(204)
                .body(emptyOrNullString());
        findTokenGetsStatusCode(defaultTestToken.getSecureRedirectToken(), 404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private ValidatableResponse findTokenGetsStatusCode(String secureRedirectToken, int expectedStatusCode) {
        return givenSetup()
                .get(tokensUrlFor(secureRedirectToken) + "/charge")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(JSON);
    }

    private RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }
}
