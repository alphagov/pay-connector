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
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestToken;
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
    }

    @After
    public void tearDown() {
        databaseTestHelper.truncateAllData();
    }

    @Test
    public void shouldSuccessfullyGetChargeForToken() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestToken()
                .withCharge(defaultTestCharge)
                .insert();

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken() + "/charge")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("externalId", is(defaultTestCharge.getExternalChargeId()))
                .body("status", is(CREATED.getValue()))
                .body("gatewayAccount.service_name", is(defaultTestAccount.getServiceName()));
    }

    @Test
    public void shouldReturn404ForChargeWhenTokenNotFound() {
        givenSetup()
                .get("/v1/frontend/tokens/non-existent-secure-redirect-token/charge")
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void shouldSuccessfullyDeleteToken() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestToken()
                .withCharge(defaultTestCharge)
                .insert();

        givenSetup()
                .delete("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(204)
                .body(emptyOrNullString());

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken() + "/charge")
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private RequestSpecification givenSetup() {
        return given().port(testContext.getPort())
                .contentType(JSON);
    }

}
