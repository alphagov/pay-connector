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
import java.util.Map;

import static java.lang.String.format;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class StripeAccountSetupResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    public static GatewayAccountResourceITBaseExtensions testBaseExtension = new GatewayAccountResourceITBaseExtensions("sandbox", app.getLocalPort());

    @Nested
    class ByAccountId {
        @Nested
        class GetStripeSetup {
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
        class PatchStripeSetup {

            @Test
            void withSingleUpdate_shouldUpdateSetup() {
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
            void withMultipleUpdates_shouldUpdateSetup() {
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
            void forInvalidOperation_shouldReturn400_withValidationError() {
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
            void forGatewayAccountDoesNotExist_shouldReturn404() {
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
            void forGatewayAccountNotStripe_shouldReturn200() {
                long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("worldpay"));
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
            void forPatchDirectorWithFalse_shouldUpdateSetup() {
                long gatewayAccountId = Long.parseLong(testBaseExtension.createAGatewayAccountFor("stripe"));
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
        }
    }
    
    @Nested
    class ByServiceIdAndAccountType {
        @Nested
        class GetStripeSetup {
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
                testBaseExtension.createAGatewayAccountWithServiceId("a-valid-service-id", "worldpay");
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
                        .body(toJson(Collections.singletonList(ImmutableMap.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/%s/stripe-setup", VALID_SERVICE_ID, TEST))
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
                        .get(format("/v1/api/service/%s/%s/stripe-setup", VALID_SERVICE_ID, TEST))
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
                                "payment_provider", "stripe",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts");
                
                app.givenSetup()
                        .body(toJson(Collections.singletonList(ImmutableMap.of(
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
                        .body(toJson(Collections.singletonList(ImmutableMap.of(
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
                        .body(toJson(Collections.singletonList(ImmutableMap.of(
                                "op", "replace",
                                "path", "bank_account",
                                "value", true))))
                        .patch(format("/v1/api/service/%s/account/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200);
            }

            @Test
            void forPatchDirectorWithFalse_shouldUpdateSetup() {
                long gatewayAccountId = Long.parseLong(app.givenSetup()
                        .body(toJson(Map.of(
                                "service_id", VALID_SERVICE_ID,
                                "type", TEST,
                                "payment_provider", "worldpay",
                                "service_name", VALID_SERVICE_NAME
                        )))
                        .post("/v1/api/accounts")
                        .then().extract().path("gateway_account_id"));
                addCompletedTask(gatewayAccountId, StripeAccountSetupTask.DIRECTOR);

                app.givenSetup()
                        .get(format("/v1/api/service/%s/%s/stripe-setup", VALID_SERVICE_ID, TEST))
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
                        .get(format("/v1/api/service/%s/%s/stripe-setup", VALID_SERVICE_ID, TEST))
                        .then()
                        .statusCode(200)
                        .body("director", is(false));
            }
        }
    }

    private void addCompletedTask(long gatewayAccountId, StripeAccountSetupTask task) {
        app.getDatabaseTestHelper().addGatewayAccountsStripeSetupTask(gatewayAccountId, task);
    }

}
