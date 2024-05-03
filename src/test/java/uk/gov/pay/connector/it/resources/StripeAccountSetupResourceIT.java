package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils;
import org.testcontainers.shaded.org.bouncycastle.crypto.prng.RandomGenerator;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountSetupResourceIT {
    @RegisterExtension
    public static GatewayAccountResourceITBaseExtensions app = new GatewayAccountResourceITBaseExtensions("stripe");

    @Test
    public void getStripeSetupWithNoTasksCompletedReturnsFalseFlags() {
        String gatewayAccountId = app.createAGatewayAccountFor("stripe");

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(false))
                .body("responsible_person", is(false))
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("director", is(false))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
    }

    @Test
    public void getStripeSetupWithSomeTasksCompletedReturnsAppropriateFlags() {
        long gatewayAccountId = Long.parseLong(app.createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.BANK_ACCOUNT);
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.VAT_NUMBER);
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(false))
                .body("vat_number", is(true))
                .body("director", is(true))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
    }

    @Test
    public void getStripeSetupGatewayAccountDoesNotExist() {
        long notFoundGatewayAccountId = RandomUtils.nextLong(1000L, 10000L);

        app.givenSetup()
                .get("/v1/api/accounts/" + notFoundGatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(404);
    }

    @Test
    public void getStripeSetupGatewayAccountIsNotAStripeAccount() {
        long gatewayAccountId = Long.parseLong(app.createAGatewayAccountFor("worldpay"));
        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);
    }

    @Test
    public void patchStripeSetupWithSingleUpdate() {
        long gatewayAccountId = Long.parseLong(app.createAGatewayAccountFor("stripe"));
        app.givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "bank_account",
                        "value", true))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(false))
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("government_entity_document", is(false))
                .body("organisation_details", is(false));
    }

    @Test
    public void patchStripeSetupWithMultipleUpdates() {
        long gatewayAccountId = Long.parseLong(app.createAGatewayAccountFor("stripe"));
        app.givenSetup()
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

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(true))
                .body("responsible_person", is(true))
                .body("vat_number", is(true))
                .body("company_number", is(true))
                .body("director", is(true))
                .body("government_entity_document", is(true))
                .body("organisation_details", is(true));
    }

    @Test
    public void patchStripeSetupValidationError() {
        long gatewayAccountId = Long.parseLong(app.createAGatewayAccountFor("stripe"));
        app.givenSetup()
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
        app.givenSetup()
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
        long gatewayAccountId = Long.valueOf(app.createAGatewayAccountFor("worldpay"));
        app.givenSetup()
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
        long gatewayAccountId = Long.valueOf(app.createAGatewayAccountFor("stripe"));
        addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("bank_account", is(false))
                .body("responsible_person", is(false))
                .body("vat_number", is(false))
                .body("company_number", is(false))
                .body("director", is(true));

        app.givenSetup()
                .body(toJson(Collections.singletonList(ImmutableMap.of(
                        "op", "replace",
                        "path", "director",
                        "value", false))))
                .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200);

        app.givenSetup()
                .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                .then()
                .statusCode(200)
                .body("director", is(false));
    }

    private void addCompletedTask(long gatewayAccountId, StripeAccountSetupTask task) {
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, task);
    }

}
