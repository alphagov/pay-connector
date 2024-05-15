package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.it.resources.GatewayAccountResourceITBaseExtensions.ACCOUNTS_API_URL;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class EmailNotificationResourceIT {
    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = AppWithPostgresAndSqsExtension.withPersistence();


    @Test
    void patchEmailNotification_shouldNotUpdateIfMissingField() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        app.givenSetup().accept(JSON)
                .body(new HashMap<>())
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(400)
                .body("message", contains("Bad patch parameters{}"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void patchEmailNotification_shouldNotUpdateIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        String templateBody = "lorem ipsum";

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/payment_confirmed/" + EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY, templateBody))
                .patch(ACCOUNTS_API_URL + nonExistingAccountId + "/email-notification")
                .then()
                .statusCode(404)
                .body("message", contains("The gateway account id '111111111' does not exist"))
                .body("error_identifier", is(ErrorIdentifier.GENERIC.toString()));
    }

    @Test
    void patchEnableNotification_shouldUpdateSuccessfullyRefundNotifications() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/refund_issued/enabled", true))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail.get("enabled"), is(true));
        assertThat(refundEmail.get("enabled"), is(true));
    }

    @Test
    void patchEnableNotification_shouldUpdateSuccessfullyConfirmationNotifications() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/payment_confirmed/enabled", false))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail.get("enabled"), is(false));
        assertThat(refundEmail.get("enabled"), is(true));
    }

    @Test
    void patchTemplateBodyNotification_shouldUpdateSuccessfullyRefundNotifications() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();
        String newTemplateBody = "new value";

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/refund_issued/template_body", newTemplateBody))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail.get("template_body"), is("Lorem ipsum dolor sit amet, consectetur adipiscing elit."));
        assertThat(refundEmail.get("template_body"), is(newTemplateBody));
    }

    @Test
    void patchTemplateBodyNotification_shouldUpdateSuccessfullyConfirmationNotifications() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();
        String newTemplateBody = "new value";

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace",
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
    void patchTemplateBodyNotification_shouldUpdateSuccessfullyNotificationIfMissing() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withEmailNotifications(new HashMap<>())
                .insert();
        String newTemplateBody = "new value";

        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/payment_confirmed/template_body", newTemplateBody))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail.get("template_body"), is(newTemplateBody));
        assertThat(refundEmail, is(nullValue()));
    }

    @Test
    void patchEnabledNotification_shouldUpdateSuccessfullyNotificationIfMissing() {
        DatabaseFixtures.TestAccount testAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .withEmailNotifications(new HashMap<>())
                .insert();
        app.givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", "/refund_issued/enabled", false))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail, is(nullValue()));
        assertThat(refundEmail.get("enabled"), is(false));
    }


    private String getPatchRequestBody(String operation, String path, Object value) {
        return toJson(ImmutableMap.of("op", operation, "path", path, "value", value));
    }
}
