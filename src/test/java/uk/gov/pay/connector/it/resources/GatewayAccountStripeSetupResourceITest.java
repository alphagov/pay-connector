package uk.gov.pay.connector.it.resources;

import io.restassured.specification.RequestSpecification;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetupTask;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.is;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class GatewayAccountStripeSetupResourceITest extends GatewayAccountResourceTestBase {
 
    protected RequestSpecification givenSetup() {
        return given().port(testContext.getPort()).contentType(JSON);
    }

    @Test
    public void getStripeSetupWithNoTasksCompletedReturnsFalseFlags() {
        String gatewayAccountId = createAGatewayAccountFor("stripe");

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account_details", is(false))
                .body("responsible_person", is(false))
                .body("organisation_vat_number_company_number", is(false));
    }

    @Test
    public void getStripeSetupWithSomeTasksCompletedReturnsAppopriateFlags() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, GatewayAccountStripeSetupTask.BANK_ACCOUNT_DETAILS);
        addCompletedTask(gatewayAccountId, GatewayAccountStripeSetupTask.ORGANISATION_VAT_NUMBER_COMPANY_NUMBER);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account_details", is(true))
                .body("responsible_person", is(false))
                .body("organisation_vat_number_company_number", is(true));
    }

    @Test
    public void getStripeSetupGatewayAccountDoesNotExist() {
        long notFoundGatewayAccountId = 13;
        
        givenSetup()
                .get("/v1/api/accounts/" + notFoundGatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(404);
    }

    @Test
    public void getStripeSetupGatewayAccountIsNotAStripeAccount() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("worldpay"));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(404);
    }

    private void addCompletedTask(long gatewayAccountId, GatewayAccountStripeSetupTask task) {
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, task);
    }
}
