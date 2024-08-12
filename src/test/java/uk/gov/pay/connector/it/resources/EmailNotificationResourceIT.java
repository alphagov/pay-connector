package uk.gov.pay.connector.it.resources;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.usernotification.model.domain.EmailNotificationType;
import uk.gov.pay.connector.usernotification.resource.EmailNotificationResource;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.http.ContentType.JSON;
import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITHelpers.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class EmailNotificationResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();
    
    @Nested
    class PatchByGatewayAccountId {
        @Test
        void patchEmailNotification_shouldNotUpdateIfMissingField() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .insert();

            app.givenSetup().accept(JSON)
                    .body(new HashMap<>())
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(422)
                    .body("message", containsInAnyOrder("The op field must be 'replace'", "The paths field must be one of: [/refund_issued/template_body, /refund_issued/enabled, /payment_confirmed/template_body, /payment_confirmed/enabled]"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void patchEmailNotification_shouldNotUpdateIfAccountIdDoesNotExist() {
            String nonExistingAccountId = "111111111";
            String templateBody = "lorem ipsum";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/payment_confirmed/" + EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY, templateBody))
                    .patch(ACCOUNTS_API_URL + nonExistingAccountId + "/email-notification")
                    .then()
                    .statusCode(404)
                    .body("message", contains("The gateway account id '111111111' does not exist"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void updateRefundIssuedEmailToBeEnabledSuccessfully() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .insert();

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/refund_issued/enabled", true))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("enabled"), is(true));
            assertThat(refundEmail.get("enabled"), is(true));
        }

        @Test
        void updatePaymentConfirmedEmailToBeDisabledSuccessfully() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .insert();

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/payment_confirmed/enabled", false))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("enabled"), is(false));
            assertThat(refundEmail.get("enabled"), is(true));
        }

        @Test
        void updateRefundIssuedTemplateBodySuccessfully() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .insert();
            String newTemplateBody = "new value";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/refund_issued/template_body", newTemplateBody))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("template_body"), is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
            assertThat(refundEmail.get("template_body"), is(newTemplateBody));
        }

        @Test
        void updatePaymentConfirmedTemplateBodySuccessfully() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .insert();
            String newTemplateBody = "new value";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody(
                            "/payment_confirmed/template_body", newTemplateBody))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("template_body"), is(newTemplateBody));
            assertThat(refundEmail.get("template_body"), is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
        }

        @Test
        void updatePaymentConfirmedTemplateBodySuccessfully_ifAccountHasNoExistingEmailNotificationsSettings() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .withEmailNotifications(new HashMap<>())
                    .insert();
            String newTemplateBody = "new value";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/payment_confirmed/template_body", newTemplateBody))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("template_body"), is(newTemplateBody));
            assertThat(refundEmail, is(nullValue()));
        }

        @Test
        void updateRefundIssuedTemplateBodySuccessfully_ifAccountHasNoExistingEmailNotificationsSettings() {
            DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                    .aTestAccount()
                    .withEmailNotifications(new HashMap<>())
                    .insert();
            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/refund_issued/enabled", false))
                    .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail, is(nullValue()));
            assertThat(refundEmail.get("enabled"), is(false));
        }
    }
    
    @Nested
    class PatchByServiceIdAndAccountType {
        String VALID_SERVICE_ID = "a-valid-service-id";
        String VALID_SERVICE_NAME = "a-test-service";
        @Test
        void forEmptyRequest_shouldReturn422() {
            app.givenSetup()
                    .body(toJson(Map.of(
                            "service_id", VALID_SERVICE_ID,
                            "type", TEST,
                            "payment_provider", "worldpay",
                            "service_name", VALID_SERVICE_NAME
                    )))
                    .post("/v1/api/accounts");
                    
            app.givenSetup().accept(JSON)
                    .body(new HashMap<>())
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", VALID_SERVICE_ID, TEST))
                    .then()
                    .statusCode(422)
                    .body("message", containsInAnyOrder("The op field must be 'replace'", "The paths field must be one of: [/refund_issued/template_body, /refund_issued/enabled, /payment_confirmed/template_body, /payment_confirmed/enabled]"))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void forNonExistentGatewayAccount_shouldReturn404() {
            String nonExistentServiceId = "a-non-existent-service-id";
            String templateBody = "lorem ipsum";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/payment_confirmed/" + EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY, templateBody))
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", nonExistentServiceId, TEST))
                    .then()
                    .statusCode(404)
                    .body("message[0]", is(format("Gateway account not found for service ID [%s] and account type [%s]", nonExistentServiceId, TEST)))
                    .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
        }

        @Test
        void forValidRequest_withRefundIssuedEnabled_shouldUpdateRefundEmail() {
            long gatewayAccountId = Long.parseLong(app.givenSetup()
                    .body(toJson(Map.of(
                            "service_id", VALID_SERVICE_ID,
                            "type", TEST,
                            "payment_provider", "worldpay",
                            "service_name", VALID_SERVICE_NAME
                    )))
                    .post("/v1/api/accounts")
                    .then().extract().path("gateway_account_id"));

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/refund_issued/enabled", true))
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", VALID_SERVICE_ID, TEST))
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("enabled"), is(true));
            assertThat(refundEmail.get("enabled"), is(true));
        }

        @Test
        void forValidRequest_withPaymentConfirmedDisabled_shouldUpdateConfirmationEmail() {
            long gatewayAccountId = Long.parseLong(app.givenSetup()
                    .body(toJson(Map.of(
                            "service_id", VALID_SERVICE_ID,
                            "type", TEST,
                            "payment_provider", "worldpay",
                            "service_name", VALID_SERVICE_NAME
                    )))
                    .post("/v1/api/accounts")
                    .then().extract().path("gateway_account_id"));

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/payment_confirmed/enabled", false))
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", VALID_SERVICE_ID, TEST))
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("enabled"), is(false));
            assertThat(refundEmail.get("enabled"), is(true));
        }

        @Test
        void forValidRequest_withRefundIssuedTemplateBody_shouldUpdateRefundEmail() {
            long gatewayAccountId = Long.parseLong(app.givenSetup()
                    .body(toJson(Map.of(
                            "service_id", VALID_SERVICE_ID,
                            "type", TEST,
                            "payment_provider", "worldpay",
                            "service_name", VALID_SERVICE_NAME
                    )))
                    .post("/v1/api/accounts")
                    .then().extract().path("gateway_account_id"));
            String newTemplateBody = "new value";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody("/refund_issued/template_body", newTemplateBody))
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", VALID_SERVICE_ID, TEST))
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("template_body"), nullValue());
            assertThat(refundEmail.get("template_body"), is(newTemplateBody));
        }

        @Test
        void forValidRequest_withPaymentConfirmedTemplateBody_shouldUpdateConfirmationEmail() {
            long gatewayAccountId = Long.parseLong(app.givenSetup()
                    .body(toJson(Map.of(
                            "service_id", VALID_SERVICE_ID,
                            "type", TEST,
                            "payment_provider", "worldpay",
                            "service_name", VALID_SERVICE_NAME
                    )))
                    .post("/v1/api/accounts")
                    .then().extract().path("gateway_account_id"));
            String newTemplateBody = "new value";

            app.givenSetup().accept(JSON)
                    .body(getPatchRequestBody(
                            "/payment_confirmed/template_body", newTemplateBody))
                    .patch(format("/v1/api/service/%s/account/%s/email-notification", VALID_SERVICE_ID, TEST))
                    .then()
                    .statusCode(200);

            Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.PAYMENT_CONFIRMED);
            Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(gatewayAccountId, EmailNotificationType.REFUND_ISSUED);
            assertThat(confirmationEmail.get("template_body"), is(newTemplateBody));
            assertThat(refundEmail.get("template_body"), nullValue());
        }
    }

    private String getPatchRequestBody(String path, Object value) {
        return toJson(Map.of("op", "replace", "path", path, "value", value));
    }
}
