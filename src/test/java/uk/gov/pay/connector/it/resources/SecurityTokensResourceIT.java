package uk.gov.pay.connector.it.resources;

import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.it.base.ChargingITestBaseExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.it.dao.DatabaseFixtures.TestToken;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.service.payments.commons.model.AuthorisationMode.MOTO_API;

public class SecurityTokensResourceIT {
    @RegisterExtension
    public static ChargingITestBaseExtension app = new ChargingITestBaseExtension("sandbox");

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge defaultTestCharge;
    
    @BeforeEach
    void setup() {

        defaultTestAccount = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestAccount()
                .insert();

        defaultTestCharge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .insert();
    }

    @Test
    void shouldReturn404ForGetTokenAndMotoAPIPayment() {
        DatabaseFixtures.TestCharge charge = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestCharge()
                .withAuthorisationMode(MOTO_API)
                .withTestAccount(defaultTestAccount)
                .insert();
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(charge)
                .withUsed(false)
                .insert();

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldGetUnusedToken() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(defaultTestCharge)
                .withUsed(false)
                .insert();

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("used", is(false))
                .body("charge.charge_id", is(defaultTestCharge.getExternalChargeId()))
                .body("charge.status", is(defaultTestCharge.getChargeStatus().toString()))
                .body("charge.gateway_account.service_name", is(defaultTestAccount.getServiceName()));
    }

    @Test
    void shouldGetUsedToken() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(defaultTestCharge)
                .withUsed(true)
                .insert();

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("used", is(true))
                .body("charge.charge_id", is(defaultTestCharge.getExternalChargeId()))
                .body("charge.status", is(defaultTestCharge.getChargeStatus().toString()))
                .body("charge.gateway_account.service_name", is(defaultTestAccount.getServiceName()));
    }

    @Test
    void shouldReturn404ForTokenNotFound() {
        givenSetup()
                .get("/v1/frontend/tokens/non-existent-secure-redirect-token")
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldDeleteToken() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(defaultTestCharge)
                .withUsed(false)
                .insert();

        givenSetup()
                .delete("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(204)
                .body(emptyOrNullString());

        givenSetup()
                .get("/v1/frontend/tokens/" + token.getSecureRedirectToken())
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void shouldMarkTokenAsUsed() {
        TestToken token = DatabaseFixtures
                .withDatabaseTestHelper(app.getDatabaseTestHelper())
                .aTestToken()
                .withCharge(defaultTestCharge)
                .withUsed(false)
                .insert();

        givenSetup()
                .post("/v1/frontend/tokens/" + token.getSecureRedirectToken() + "/used")
                .then()
                .statusCode(204)
                .body(emptyOrNullString());

        assertTrue(app.getDatabaseTestHelper().isChargeTokenUsed(token.getSecureRedirectToken()));
    }

    @Test
    void shouldReturn404WhenTryingToMarkNotFoundTokenUsed() {
        givenSetup()
                .post("/v1/frontend/tokens/" + "not_found" + "/used")
                .then()
                .statusCode(404)
                .body("message", contains("Token invalid!"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    private RequestSpecification givenSetup() {
        return given().port(app.getLocalPort())
                .contentType(JSON);
    }

}
