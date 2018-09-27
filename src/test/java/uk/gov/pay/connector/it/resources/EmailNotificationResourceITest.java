package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.model.domain.EmailNotificationType;
import uk.gov.pay.connector.resources.EmailNotificationResource;

import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class EmailNotificationResourceITest extends GatewayAccountResourceTestBase {

    @Test
    public void updateEmailNotification_shouldUpdateSuccessfullyIfEmailNotificationAlreadyExists() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        String templateBody = "lorem ipsum";
        givenSetup().accept(JSON)
                .body(ImmutableMap.of(EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD, templateBody))
                .post(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        assertThat(emailNotification.get("template_body"), is(templateBody));
        assertThat(emailNotification.get("enabled"), is(true));
    }

    @Test
    public void updateEmailNotification_shouldUpdateSuccessfullyIfEmailNotificationDoesNotExist() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withEmailNotifications(new HashMap<>())
                .insert();

        String templateBody = "lorem ipsum";
        givenSetup().accept(JSON)
                .body(ImmutableMap.of(EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD, templateBody))
                .post(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        assertThat(emailNotification.get("template_body"), is(templateBody));
        assertThat(emailNotification.get("enabled"), is(true));
    }

    @Test
    public void updateEmailNotification_shouldTurnOnEmailNotificationsSuccessfullyIfEmailNotificationDoesNotExist() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", EmailNotificationResource.EMAIL_NOTIFICATION_ENABLED, true))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        assertThat(emailNotification.get("enabled"), is(true));
    }

    @Test
    public void updateEmailNotification_shouldNotUpdateIfMissingField() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
                .body(new HashMap<>())
                .post(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [custom-email-text]"));
    }

    @Test
    public void updateEmailNotification_shouldNotUpdateIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        String templateBody = "lorem ipsum";

        givenSetup().accept(JSON)
                .body(ImmutableMap.of(EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY_OLD, templateBody))
                .post(ACCOUNTS_API_URL + nonExistingAccountId + "/email-notification")
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

    @Test
    public void disableEmailNotification_shouldUpdateSuccessfully() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", EmailNotificationResource.EMAIL_NOTIFICATION_ENABLED, false))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        assertThat(emailNotification.get("enabled"), is(false));
    }

    // PP-4111 Old test (using the old patch path)
    @Test
    public void patchEnableNotification_shouldUpdateSuccessfully() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", EmailNotificationResource.EMAIL_NOTIFICATION_ENABLED, false))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        assertThat(emailNotification.get("enabled"), is(false));
    }

    @Test
    public void patchEnableNotification_shouldUpdateSuccessfullyRefundNotifications() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
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
    public void patchEnableNotification_shouldUpdateSuccessfullyConfirmationNotifications() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        givenSetup().accept(JSON)
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
    public void patchTemplateBodyNotification_shouldUpdateSuccessfullyRefundNotifications() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();
        String newTemplateBody = "new value";

        givenSetup().accept(JSON)
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
    public void patchTemplateBodyNotification_shouldUpdateSuccessfullyConfirmationNotifications() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();
        String newTemplateBody = "new value";

        givenSetup().accept(JSON)
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
    public void patchTemplateBodyNotification_shouldUpdateSuccessfullyNotificationIfMissing() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withEmailNotifications(new HashMap<>())
                .insert();
        String newTemplateBody = "new value";

        givenSetup().accept(JSON)
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
    public void patchEnabledNotification_shouldUpdateSuccessfullyNotificationIfMissing() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .withEmailNotifications(new HashMap<>())
                .insert();
        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace","/refund_issued/enabled", false))
                .patch(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> confirmationEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.PAYMENT_CONFIRMED);
        Map<String, Object> refundEmail = app.getDatabaseTestHelper().getEmailForAccountAndType(testAccount.getAccountId(), EmailNotificationType.REFUND_ISSUED);
        assertThat(confirmationEmail, is(nullValue()));
        assertThat(refundEmail.get("enabled"), is(false));
    }
    
    @Test
    public void getEmailNotificationSuccessfully() {
        DatabaseFixtures.TestAccount testAccount = databaseFixtures
                .aTestAccount()
                .insert();

        String templateBody = "lorem ipsum";
        app.getDatabaseTestHelper().updateEmailNotification(testAccount.getAccountId(), templateBody, true, EmailNotificationType.PAYMENT_CONFIRMED);

        givenSetup()
                .accept(JSON)
                .get(ACCOUNTS_API_URL + testAccount.getAccountId() + "/email-notification")
                .then()
                .statusCode(200)
                .body("template_body", is(templateBody))
                .body("enabled", is(true));
    }

    private String getPatchRequestBody(String operation, String path, Object value) {
        return toJson(ImmutableMap.of("op",operation, "path", path, "value", value));
    }
}
