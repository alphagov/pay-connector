package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;

public class AdyenNotificationResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/adyen/payments";

    @Test
    void shouldHandleAValidJsonNotification() {
        given()
                .port(app.getLocalPort())
                .body("{\"notificationItems\":[{\"NotificationRequestItem\":{\"eventCode\":\"AUTHORISATION\"}}]}")
                .header("X-Forwarded-For", "5.6.7.8")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

    @Test
    void shouldRejectUnsupportedHttpMethod() {
        given()
                .port(app.getLocalPort())
                .get(NOTIFICATION_PATH)
                .then()
                .statusCode(405);
    }

    @Test
    void shouldRejectUnsupportedContentType() {
        given()
                .port(app.getLocalPort())
                .body("{\"notificationItems\":[]}")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }
}
