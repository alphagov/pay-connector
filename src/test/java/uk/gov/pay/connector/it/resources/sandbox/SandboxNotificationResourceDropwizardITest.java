package uk.gov.pay.connector.it.resources.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class SandboxNotificationResourceDropwizardITest extends ChargingITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/sandbox";

    public SandboxNotificationResourceDropwizardITest() {
        super("sandbox");
    }

    @Test
    public void shouldReturn200ForSandboxNotifications() throws Exception {
        given().port(app.getLocalPort())
                .body("sandbox-notification")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

}
