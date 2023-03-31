package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import io.restassured.specification.RequestSpecification;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.service.payments.commons.model.ErrorIdentifier;
import uk.gov.pay.connector.app.ConnectorApp;
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
@Ignore
public class StripeAccountSetupResourceIT extends GatewayAccountResourceTestBase {

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
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("director", is(false))
                .body("additional_kyc_data", is(false))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
    }

    @Test
    public void getStripeSetupWithSomeTasksCompletedReturnsAppropriateFlags() {
        long gatewayAccountId = Long.parseLong(createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.BANK_ACCOUNT);
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.VAT_NUMBER);
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(false))
                .body("vat_number", is(true))
                .body("director", is(true))
                .body("additional_kyc_data", is(false))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
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
        long gatewayAccountId = Long.parseLong(createAGatewayAccountFor("worldpay"));
        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);
    }

    @Test
    public void patchStripeSetupWithSingleUpdate() {
        long gatewayAccountId = Long.parseLong(createAGatewayAccountFor("stripe"));
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
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("additional_kyc_data", is(false))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
    }

    @Test
    public void patchStripeSetupWithMultipleUpdates() {
        long gatewayAccountId = Long.parseLong(createAGatewayAccountFor("stripe"));
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
                                "path", "vat_number",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "company_number",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "director",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "additional_kyc_data",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "government_entity_document",
                                "value", true),
                        ImmutableMap.of(
                                "op", "replace",
                                "path", "organisation_details",
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
                .body("vat_number", is(true))
                .body("company_number", is(true))
                .body("director", is(true))
                .body("additional_kyc_data", is(true))
                .body("government_entity_document", is(true))
                .body("organisation_details", is(true));
    }

    @Test
    public void patchStripeSetupValidationError() {
        long gatewayAccountId = Long.parseLong(createAGatewayAccountFor("stripe"));
        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "remove",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(400)
                .body("message", contains("Operation [remove] not supported for path [bank_account]"))
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
                .statusCode(200);
    }

    @Test
    public void patchStripeSetupDirectorWithFalse() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(false))
                .body("responsible_person", is(false))
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("director", is(true))
                .body("additional_kyc_data", is(false));

        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "director",
                        "value", false))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("director", is(false));
    }

    @Test
    public void patchStripeSetupAdditionalKycDataWithFalse() {
        long gatewayAccountId = Long.valueOf(createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.ADDITIONAL_KYC_DATA);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(false))
                .body("responsible_person", is(false))
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("director", is(false))
                .body("additional_kyc_data", is(true));

        givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "additional_kyc_data",
                        "value", false))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("additional_kyc_data", is(false));
    }

    private void addCompletedTask(long gatewayAccountId, StripeAccountSetupTask task) {
        databaseTestHelper.addGatewayAccountsStripeSetupTask(gatewayAccountId, task);
    }

}
