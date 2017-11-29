package uk.gov.pay.connector.it.resources.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.it.base.ChargingITestBase;

import static com.jayway.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class SandboxNotificationResourceITest extends ChargingITestBase {

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/sandbox";

    public SandboxNotificationResourceITest() {
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
