package uk.gov.pay.connector.it.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.CreateGatewayAccountPayloadBuilder.aCreateGatewayAccountPayloadBuilder;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class GatewayAccountResourceUpdateIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    public static GatewayAccountResourceITHelpers testBaseExtension = new GatewayAccountResourceITHelpers(app.getLocalPort());

    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Nested
    class PatchByServiceIdAndAccountType {

        private String serviceId;

        @BeforeEach
        void before() {
            serviceId = RandomIdGenerator.newId();
            Map<String, String> gatewayAccountRequest = Map.of(
                    "payment_provider", "worldpay",
                    "service_id", serviceId,
                    "service_name", "Service Name",
                    "type", "test");

            app.givenSetup().body(toJson(gatewayAccountRequest)).post(ACCOUNTS_API_URL);
        }

        @Test
        void updateWorldpayExemptionEngineEnabledFlagSuccessfully() {
            Map<String, String> valid3dsFlexCredentialsPayload = Map.of(
                    "issuer", "53f0917f101a4428b69d5fb0", // pragma: allowlist secret
                    "organisational_unit_id", "57992a087a0c4849895ab8a2", // pragma: allowlist secret
                    "jwt_mac_key", "4cabd5d2-0133-4e82-b0e5-2024dbeddaa9");

            app.givenSetup().body(toJson(valid3dsFlexCredentialsPayload))
                    .put(format("/v1/api/service/%s/account/%s/3ds-flex-credentials", serviceId, TEST));

            Map<String, Object> payload = Map.of(
                    "op", "replace",
                    "path", "worldpay_exemption_engine_enabled",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("worldpay_3ds_flex.exemption_engine_enabled", is(true));

            payload = Map.of(
                    "op", "replace",
                    "path", "worldpay_exemption_engine_enabled",
                    "value", false);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("worldpay_3ds_flex.exemption_engine_enabled", is(false));
        }

        @Test
        void updateNotifySettingsSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("notifySettings", nullValue());

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid",
                            "refund_issued_template_id", "anothertemplate"));

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("notifySettings.api_token", is("anapitoken"))
                    .body("notifySettings.template_id", is("atemplateid"))
                    .body("notifySettings.refund_issued_template_id", is("anothertemplate"));

            payload = Map.of("op", "remove",
                    "path", "notify_settings");

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("notifySettings", nullValue());
        }
        
        @Test
        void returnBadRequestWhenNotifySettingsIsUpdatedWithWrongOp() {
            Map<String, Object> payload = Map.of("op", "insert",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid"));

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then().log().body()
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is("GENERIC"))
                    .body("message", contains("Operation [insert] is not valid for path [notify_settings]"));
        }

        @Test
        void returnBadRequestWhenUpdatingWrongPath() {
            Map<String, Object> payload = Map.of("op", "insert",
                    "path", "wrong_path",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid"));

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is("GENERIC"))
                    .body("message", contains("Operation [op] not supported for path [wrong_path]"));
        }

        @Test
        void updateBlockPrepaidCardsSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("block_prepaid_cards", is(false));

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "block_prepaid_cards",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("block_prepaid_cards", is(true));
        }

        @Test
        void updateEmailCollectionModeSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("email_collection_mode", is("MANDATORY"));

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "email_collection_mode",
                    "value", "OFF");

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("email_collection_mode", is("OFF"));
        }

        @Test
        void returnBadRequestWhenEmailCollectionModeIsUpdatedWithWrongValue() {
            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "email_collection_mode",
                    "value", "nope");

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(BAD_REQUEST.getStatusCode())
                    .body("error_identifier", is("GENERIC"))
                    .body("message", contains("Value [nope] is not valid for [email_collection_mode]"));
        }
        
        @Test
        void updateCorporateCardAmountsSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "corporate_credit_card_surcharge_amount",
                    "value", 100);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            payload = Map.of("op", "replace",
                    "path", "corporate_debit_card_surcharge_amount",
                    "value", 200);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            payload = Map.of("op", "replace",
                    "path", "corporate_prepaid_debit_card_surcharge_amount",
                    "value", 400);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("corporate_credit_card_surcharge_amount", is(100))
                    .body("corporate_debit_card_surcharge_amount", is(200))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(400));
        }

        @Test
        void updateAllowTelephonePaymentNotificationsSuccessfully() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("allow_telephone_payment_notifications", is(false));

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "allow_telephone_payment_notifications",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("allow_telephone_payment_notifications", is(true));
        }
        
        @Test
        void returnNotFoundForNonExistentAccountType() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", true)))
                    .patch(format("/v1/api/service/%s/live/", serviceId))
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode());
        }
        
        @Test
        void returnNotFoundForNonExistentServiceId() {
            app.givenSetup()
                    .body(toJson(Map.of("op", "replace",
                            "path", "allow_telephone_payment_notifications",
                            "value", true)))
                    .patch("/v1/api/nexiste-pas/test/")
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode());
        }

        @Test
        void updatingDisabledToFalseShouldClearDisabledReason() {
            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("disabled", is(false));

            Map<String, Object> payload = Map.of("op", "replace",
                    "path", "disabled",
                    "value", true);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            payload = Map.of("op", "replace",
                    "path", "disabled_reason",
                    "value", "Disabled because Dolores Umbridge is evil");

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("disabled", is(true))
                    .body("disabled_reason", is("Disabled because Dolores Umbridge is evil"));

            payload = Map.of("op", "replace",
                    "path", "disabled",
                    "value", false);

            app.givenSetup()
                    .body(toJson(payload))
                    .patch(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get(format("/v1/api/service/%s/account/test", serviceId))
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("disabled", is(false))
                    .body("disabled_reason", is(nullValue()));
        }
    }

    @Nested
    class PatchByGatewayAccountId {
        private String gatewayAccountId;
        
        @BeforeEach
        void createGatewayAccount() {
            Map<String, String> createAccountPayload = aCreateGatewayAccountPayloadBuilder().withProvider("worldpay").build();
            gatewayAccountId = testBaseExtension.createGatewayAccount(createAccountPayload);
        }
        
        @Test
        void shouldReturn200WhenWorldpayExemptionEngineEnabledIsUpdated() throws JsonProcessingException {
            app.getDatabaseTestHelper().insertWorldpay3dsFlexCredential(
                    Long.valueOf(gatewayAccountId),
                    "macKey",
                    "issuer",
                    "org_unit_id",
                    2L);
            String payload = objectMapper.writeValueAsString(Map.of(
                    "op", "replace",
                    "path", "worldpay_exemption_engine_enabled",
                    "value", true));

            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("worldpay_3ds_flex.exemption_engine_enabled", is(true));
        }

        @Test
        void shouldReturn200_whenNotifySettingsIsUpdated() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid",
                            "refund_issued_template_id", "anothertemplate")));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
        }

        @Test
        void shouldReturn400_whenNotifySettingsIsUpdated_withWrongOp() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "insert",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid")));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(BAD_REQUEST.getStatusCode());
        }

        @Test
        void shouldReturn200_whenBlockPrepaidCardsIsUpdated() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "block_prepaid_cards",
                    "value", true));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());

            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode())
                    .body("block_prepaid_cards", is(true));
        }

        @Test
        void shouldReturn200_whenEmailCollectionModeIsUpdated() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "email_collection_mode",
                    "value", "OFF"));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
        }

        @Test
        void shouldReturn400_whenEmailCollectionModeIsUpdated_withWrongValue() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "email_collection_mode",
                    "value", "nope"));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(BAD_REQUEST.getStatusCode());
        }

        @Test
        void shouldReturn404ForNotifySettings_whenGatewayAccountIsNonExistent() throws Exception {
            String nonExistentGatewayAccountId = "1000023";
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid",
                            "refund_issued_template_id", "anothertemplate")));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + nonExistentGatewayAccountId)
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode());
        }

        @Test
        void shouldReturn200_whenNotifySettingsIsRemoved() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "notify_settings",
                    "value", Map.of("api_token", "anapitoken",
                            "template_id", "atemplateid",
                            "refund_issued_template_id", "anothertemplate")));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());

            payload = objectMapper.writeValueAsString(Map.of("op", "remove",
                    "path", "notify_settings"));

            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
        }

        @Test
        void shouldReturn400_whenNotifySettingsIsRemoved_withWrongPath() throws Exception {
            String payload = objectMapper.writeValueAsString(Map.of("op", "insert",
                    "path", "notify_setting"));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(BAD_REQUEST.getStatusCode());
        }

        @Test
        void patchGatewayAccount_forCorporateCreditCardSurcharge() throws JsonProcessingException {
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "corporate_credit_card_surcharge_amount",
                    "value", 100));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(100))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        }

        @Test
        void patchGatewayAccount_forCorporateDebitCardSurcharge() throws JsonProcessingException {
            String gatewayAccountId = testBaseExtension.createGatewayAccount(aCreateGatewayAccountPayloadBuilder().build());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "corporate_debit_card_surcharge_amount",
                    "value", 200));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(200))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
        }

        @Test
        void patchGatewayAccount_forCorporatePrepaidDebitCardSurcharge() throws JsonProcessingException {
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(0));
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "corporate_prepaid_debit_card_surcharge_amount",
                    "value", 400));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("corporate_credit_card_surcharge_amount", is(0))
                    .body("corporate_debit_card_surcharge_amount", is(0))
                    .body("corporate_prepaid_debit_card_surcharge_amount", is(400));
        }

        @Test
        void patchGatewayAccount_forAllowTelephonePaymentNotifications() throws JsonProcessingException {
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("allow_telephone_payment_notifications", is(false));
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "allow_telephone_payment_notifications",
                    "value", true));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("allow_telephone_payment_notifications", is(true));
        }

        @Test
        void shouldReturn404ForCorporateSurcharge_whenGatewayAccountIsNonExistent() throws Exception {
            String nonExistentGatewayAccountId = "1000023";
            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "corporate_credit_card_surcharge_amount",
                    "value", 100));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + nonExistentGatewayAccountId)
                    .then()
                    .statusCode(NOT_FOUND.getStatusCode());
        }

        @Test
        void patchGatewayAccount_setDisabledToFalse_shouldClearDisabledReason() throws JsonProcessingException {
            long gatewayAccountIdAsLong = Long.parseLong(gatewayAccountId);
            app.getDatabaseTestHelper().setDisabled(gatewayAccountIdAsLong);
            String disabledReason = "Because reasons";
            app.getDatabaseTestHelper().setDisabledReason(gatewayAccountIdAsLong, disabledReason);

            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("disabled", is(true))
                    .body("disabled_reason", is(disabledReason));

            String payload = objectMapper.writeValueAsString(Map.of("op", "replace",
                    "path", "disabled",
                    "value", false));
            app.givenSetup()
                    .body(payload)
                    .patch("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .statusCode(OK.getStatusCode());
            app.givenSetup()
                    .get("/v1/api/accounts/" + gatewayAccountId)
                    .then()
                    .body("disabled", is(false))
                    .body("disabled_reason", is(nullValue()));
        }
    }

}
