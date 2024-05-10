package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountSetupResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions("sandbox", app.getLocalPort());

    @Nested
    class getStripeSetupByGatewayAccountId {
        @Test
        void withNoTasksCompletedReturnsFalseFlags() {
            String gatewayAccountId = testBaseExtension.createAGatewayAccountFor("stripe");

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
        void withSomeTasksCompletedReturnsAppropriateFlags() {
            long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("stripe"));
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
        void returnsNotFoundResponseWhenGatewayAccountDoesNotExist() {
            long notFoundGatewayAccountId = 13;

            app.givenSetup()
                    .get("/v1/api/accounts/" + notFoundGatewayAccountId + "/stripe-setup")
                    .then()
                    .statusCode(404);
        }

        @Test
        void returnsSuccessfulResponseWhenGatewayAccountIsNotAStripeAccount() {
            long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("worldpay"));
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                    .then()
                    .statusCode(200);
        }
    }
    
    @Nested
    class getStripeSetupByServiceIdAndAccountType {
        @Test
        void withNoTasksCompletedReturnsFalseFlags() {
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe");

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-setup")
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
        void withSomeTasksCompletedReturnsAppropriateFlags() {
            long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "stripe"));
            app.givenSetup()
                    .body(toJson(Arrays.asList(
                            ImmutableMap.of(
                                    "op", "replace",
                                    "path", "bank_account",
                                    "value", true),
                            ImmutableMap.of(
                                    "op", "replace",
                                    "path", "vat_number",
                                    "value", true),
                            ImmutableMap.of(
                                    "op", "replace",
                                    "path", "director",
                                    "value", true)
                    )))
                    .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                    .then()
                    .statusCode(200);

            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-setup")
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
        void returnsNotFoundResponseWhenNoGatewayAccountFoundForService() {
            app.givenSetup()
                    .get("/v1/api/service/unknown-service-id/TEST/stripe-setup")
                    .then()
                    .statusCode(404)
                    .body("message[0]", is("Gateway account not found for service ID [unknown-service-id] and account type [test]"));
        }

        @Test
        void returnsSuccessfulResponseWhenGatewayAccountIsNotAStripeAccount() {
            testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id","worldpay");
            app.givenSetup()
                    .get("/v1/api/service/a-valid-service-id/TEST/stripe-setup")
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
    }

    @Test
    void patchStripeSetupWithSingleUpdate() {
        long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("stripe"));
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
    void patchStripeSetupWithMultipleUpdates() {
        long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("stripe"));
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
    void patchStripeSetupValidationError() {
        long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("stripe"));
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
    void patchStripeSetupGatewayAccountDoesNotExist() {
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
    void patchStripeSetupGatewayAccountNotStripe() {
        long gatewayAccountId = Long.valueOf(testBaseExtension.createAGatewayAccountFor("worldpay"));
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
    void patchStripeSetupDirectorWithFalse() {
        long gatewayAccountId = Long.valueOf(testBaseExtension.createAGatewayAccountFor("stripe"));
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
