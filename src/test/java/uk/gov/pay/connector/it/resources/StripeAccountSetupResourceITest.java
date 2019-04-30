package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import io.restassured.specification.RequestSpecification;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.common.model.api.ErrorIdentifier;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class StripeAccountSetupResourceITest extends GatewayAccountResourceTestBase {

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
                .body("bank_account", is(false))
                .body("responsible_person", is(false))
                .body("vat_number_company_number", is(false));
    }

    @Test
    public void getStripeSetupWithSomeTasksCompletedReturnsAppropriateFlags() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.BANK_ACCOUNT);
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.VAT_NUMBER_COMPANY_NUMBER);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(false))
                .body("vat_number_company_number", is(true));
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

    @Test
    public void patchStripeSetupWithSingleUpdate() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(false))
                .body("vat_number_company_number", is(false));
    }

    @Test
    public void patchStripeSetupWithMultipleUpdates() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        givenSetup()
                .body(toJson(Arrays.asList(
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "responsible_person",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "vat_number_company_number",
                                "value", true)
                )))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(true))
                .body("vat_number_company_number", is(true));
    }

    @Test
    public void patchStripeSetupValidationError() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "not_replace",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(400)
                .body("message", contains("Operation [not_replace] not supported for path [bank_account]"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    public void patchStripeSetupGatewayAccountDoesNotExist() {
        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "not_replace",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + 123 + "/stripe-setup")
                .then()
                .statusCode(404);
    }

    @Test
    public void patchStripeSetupGatewayAccountNotStripe() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("worldpay"));
        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(404);
    }

    private void addCompletedTask(long gatewayAccountId, StripeAccountSetupTask task) {
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, task);
    }

}
