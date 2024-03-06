package uk.gov.pay.connector.it.resources.sandbox;

import org.junit.Test;
import uk.gov.pay.connector.it.base.NewChargingITestBase;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class SandboxNotificationResourceIT extends NewChargingITestBase {
    private static final String SANDBOX_IP_ADDRESS = "1.1.1.1, 3.3.3.3";
    private static final String UNEXPECTED_IP_ADDRESS = "3.4.3.1, 1.1.1.1";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/sandbox";

    public SandboxNotificationResourceIT() {
        super("sandbox");
    }

    @Test
    public void shouldReturn200ForSandboxNotificationsFromValidIPAddress() {
        given().port(connectorApp.getLocalPort())
                .header("X-Forwarded-For", SANDBOX_IP_ADDRESS)
                .body("sandbox-notification")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldReturn200ForSandboxNotificationsWithValidAuthToken() {
        given().port(connectorApp.getLocalPort())
                .header("X-Forwarded-For", UNEXPECTED_IP_ADDRESS)
                .header("Authorization", "let-me-in")
                .body("sandbox-notification")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIpAddress() {
        given().port(connectorApp.getLocalPort())
                .header("X-Forwarded-For", UNEXPECTED_IP_ADDRESS)
                .body("sandbox-notification")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }
}
