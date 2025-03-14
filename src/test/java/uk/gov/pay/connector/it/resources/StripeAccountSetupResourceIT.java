package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountSetupResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITHelpers testHelpers = new GatewayAccountResourceITHelpers(app.getLocalPort());

    @Nested
    class ByAccountId {
        @Nested
        class GetStripeSetup {
            @Test
            void withNoTasksCompletedReturnsFalseFlags() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());

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
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());

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
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("worldpay").build());
                app.givenSetup()
                        .get("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                        .then()
                        .statusCode(200);
            }
        }
        
        @Nested
        class PatchStripeSetup {

            @Test
            void withSingleUpdate_shouldUpdateSetup() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
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
            void withMultipleUpdates_shouldUpdateSetup() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());
                app.givenSetup()
                        .body(toJson(Arrays.asList(
                                Map.of(
                                        "op", "replace",
                                        "path", "bank_account",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "responsible_person",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "vat_number",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "company_number",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "director",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "government_entity_document",
                                        "value", true),
                                Map.of(
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
            void forInvalidOperation_shouldReturn422_withValidationError() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "remove",
                                "path", "bank_account",
                                "value", true))))
                        .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                        .then()
                        .statusCode(422)
                        .body("message", contains("The op field must be 'replace'"))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }

            @Test
            void forGatewayAccountDoesNotExist_shouldReturn404() {
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch("/v1/api/accounts/" + 123 + "/stripe-setup")
                        .then()
                        .statusCode(404);
            }

            @Test
            void forGatewayAccountNotStripe_shouldReturn200() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                        .then()
                        .statusCode(200);
            }

            @Test
            void forPatchDirectorWithFalse_shouldUpdateSetup() {
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder().withProvider("stripe").build());
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
                        .body(toJson(Collections.singletonList(Map.of(
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
        }
    }
    
    @Nested
    class ByServiceIdAndAccountType {
        @Nested
        class GetStripeSetup {
            @Test
            void withNoTasksCompletedReturnsFalseFlags() {
                testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder()
                                .withServiceId("a-valid-service-id")
                                .withProvider("stripe")
                                .build());

                app.givenSetup()
                        .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-setup")
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
                String gatewayAccountId = testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder()
                                .withServiceId("a-valid-service-id")
                                .withProvider("stripe")
                                .build());

                app.givenSetup()
                        .body(toJson(Arrays.asList(
                                Map.of(
                                        "op", "replace",
                                        "path", "bank_account",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "vat_number",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "director",
                                        "value", true)
                        )))
                        .patch("/v1/api/accounts/" + gatewayAccountId + "/stripe-setup")
                        .then()
                        .statusCode(200);

                app.givenSetup()
                        .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-setup")
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
                        .get("/v1/api/service/unknown-service-id/account/TEST/stripe-setup")
                        .then()
                        .statusCode(404)
                        .body("message[0]", is("Gateway account not found for service external id [unknown-service-id] and account type [test]"));
            }

            @Test
            void returnsSuccessfulResponseWhenGatewayAccountIsNotAStripeAccount() {
                testHelpers.createGatewayAccount(
                        aCreateGatewayAccountPayloadBuilder()
                                .withServiceId("a-valid-service-id")
                                .withProvider("worldpay")
                                .build());
                
                app.givenSetup()
                        .get("/v1/api/service/a-valid-service-id/account/TEST/stripe-setup")
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
        @Nested
        class PatchStripeSetup {
            String VALID_SERVICE_ID = "a-valid-service-id";
            String VALID_SERVICE_NAME = "a-test-service";

            @Test
            void withSingleUpdate_shouldUpdateSetup() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "stripe",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts")
                        .then().statusCode(201);
                
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
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
            void withMultipleUpdates_shouldUpdateSetup() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "stripe",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts")
                        .then().statusCode(201);
                
                app.givenSetup()
                        .body(toJson(Arrays.asList(
                                Map.of(
                                        "op", "replace",
                                        "path", "bank_account",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "responsible_person",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "vat_number",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "company_number",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "director",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "government_entity_document",
                                        "value", true),
                                Map.of(
                                        "op", "replace",
                                        "path", "organisation_details",
                                        "value", true)
                        )))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
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
            void forInvalidOperation_shouldReturn422_withValidationError() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "worldpay",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts");
                
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "remove",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(422)
                        .body("message", contains("The op field must be 'replace'"))
                        .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
            }

            @Test
            void forGatewayAccountDoesNotExist_shouldReturn404() {
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", "not-a-service-id", TEST))
                        .then()
                        .statusCode(404);
            }

            @Test
            void forGatewayAccountNotStripe_shouldReturn200() {
                app.givenSetup()
                        .body(toJson(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "worldpay",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts")
                        .then().statusCode(201);
                
                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);
            }

            @Test
            void forPatchDirectorWithFalse_shouldUpdateSetup() {
                String gatewayAccountId = app.givenSetup()
                        .body(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "worldpay",
                                "service_name", VALID_SERVICE_NAME
                        ))
                        .post("/v1/api/accounts")
                        .then().extract().path("gateway_account_id");
                addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200)
                        .body("bank_account", is(false))
                        .body("responsible_person", is(false))
                        .body("vat_number", is(false))
                        .body("company_number", is(false))
                        .body("director", is(true));

                app.givenSetup()
                        .body(toJson(Collections.singletonList(Map.of(
                                "op", "replace",
                                "path", "director",
                                "value", false))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200)
                        .body("director", is(false));
            }
        }
    }

    private void addCompletedTask(String gatewayAccountId, StripeAccountSetupTask task) {
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(Long.parseLong(gatewayAccountId), task);
    }

}
