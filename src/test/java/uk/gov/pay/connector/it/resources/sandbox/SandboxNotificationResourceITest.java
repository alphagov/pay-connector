package uk.gov.pay.connector.it.resources.sandbox;

import com.google.common.io.Resources;
import com.jayway.restassured.response.ValidatableResponse;
import org.junit.Test;
import uk.gov.pay.connector.it.base.CardResourceITestBase;
import uk.gov.pay.connector.util.DnsUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import static com.google.common.io.Resources.getResource;
import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class SandboxNotificationResourceITest extends CardResourceITestBase {

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
