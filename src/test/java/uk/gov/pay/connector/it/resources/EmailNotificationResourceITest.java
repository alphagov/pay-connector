package uk.gov.pay.connector.it.resources;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import uk.gov.pay.connector.resources.EmailNotificationResource;

import java.util.HashMap;
import java.util.Map;

import static com.jayway.restassured.http.ContentType.JSON;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class EmailNotificationResourceITest extends GatewayAccountResourceTestBase {

    @Test
    public void updateEmailNotification_shouldUpdateSuccessfullyIfEmailNotificationAlreadyExists() {
        String accountId = createAGatewayAccountFor("smartpay");

        String templateBody = "lorem ipsum";
        givenSetup().accept(JSON)
                .body(ImmutableMap.of(EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY, templateBody))
                .post(ACCOUNTS_API_URL + accountId + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailNotificationByAccountId(Long.valueOf(accountId));
        assertThat(emailNotification.get("template_body"), is(templateBody));
        assertThat(emailNotification.get("enabled"), is(true));
    }

    @Test
    public void updateEmailNotification_shouldNotUpdateIfMissingField() {
        String accountId = createAGatewayAccountFor("worldpay");

        givenSetup().accept(JSON)
                .body(new HashMap<>())
                .post(ACCOUNTS_API_URL + accountId + "/email-notification")
                .then()
                .statusCode(400)
                .body("message", is("Field(s) missing: [custom-email-text]"));
    }

    @Test
    public void updateEmailNotification_shouldNotUpdateIfAccountIdDoesNotExist() {
        String nonExistingAccountId = "111111111";
        createAGatewayAccountFor("smartpay");
        String templateBody = "lorem ipsum";

        givenSetup().accept(JSON)
                .body(ImmutableMap.of(EmailNotificationResource.EMAIL_NOTIFICATION_TEMPLATE_BODY, templateBody))
                .post(ACCOUNTS_API_URL + nonExistingAccountId + "/email-notification")
                .then()
                .statusCode(404)
                .body("message", is("The gateway account id '111111111' does not exist"));
    }

    @Test
    public void disableEmailNotification_shouldUpdateSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");

        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", EmailNotificationResource.EMAIL_NOTIFICATION_ENABLED, false))
                .patch(ACCOUNTS_API_URL + accountId + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailNotificationByAccountId(Long.parseLong(accountId));
        assertThat(emailNotification.get("enabled"), is(false));
    }

    @Test
    public void enableEmailNotification_shouldUpdateSuccessfully() {
        String accountId = createAGatewayAccountFor("smartpay");
        app.getDatabaseTestHelper().updateEmailNotification(Long.parseLong(accountId), "lorem ipsum", false);

        givenSetup().accept(JSON)
                .body(getPatchRequestBody("replace", EmailNotificationResource.EMAIL_NOTIFICATION_ENABLED, true))
                .patch(ACCOUNTS_API_URL + accountId + "/email-notification")
                .then()
                .statusCode(200);

        Map<String, Object> emailNotification = app.getDatabaseTestHelper().getEmailNotificationByAccountId(Long.parseLong(accountId));
        assertThat(emailNotification.get("enabled"), is(true));
    }

    @Test
    public void getEmailNotificationSuccessfully() {
        String templateBody = "lorem ipsum";
        String accountId = createAGatewayAccountFor("worldpay");
        app.getDatabaseTestHelper().updateEmailNotification(Long.parseLong(accountId), templateBody, true);

        givenSetup().accept(JSON)
                .get(ACCOUNTS_API_URL + accountId + "/email-notification")
                .then()
                .statusCode(200)
                .body("template_body", is(templateBody))
                .body("enabled", is(true));
    }

    private String getPatchRequestBody(String operation, String path, boolean value) {
        return toJson(ImmutableMap.of("op",operation, "path", path, "value", value));
    }
}
