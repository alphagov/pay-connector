package uk.gov.pay.connector.it.resources.adyen;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.util.ConnectorAppWithCustomInjector;
import uk.gov.pay.connector.util.DnsPointerResourceRecord;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.ConnectorModuleWithOverrides.reverseDnsLookup;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TOKEN_CREATED_NOTIFICATION;

public class AdyenTokenNotificationResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(ConnectorAppWithCustomInjector.class);

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/adyen/tokens";
    private static final String ADYEN_IP_ADDRESS = "192.168.0.1";
    private static final String UNEXPECTED_IP_ADDRESS = "8.8.8.8";
    private static final String VALID_HMAC_SIGNATURE = "AY06WfSC+Rp6S6HKwJn0/cK/A79/b6J90ZYV9eLi/LM="; // pragma: allowlist secret

    @BeforeAll
    static void before() {
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(ADYEN_IP_ADDRESS))).thenReturn(Optional.of(".adyen.com."));
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(UNEXPECTED_IP_ADDRESS))).thenReturn(Optional.of("dns.google."));
    }

    @Test
    void shouldHandleAValidTokenNotification() {
        String payload = TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION);
        given()
                .port(app.getLocalPort())
                .body(payload)
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", VALID_HMAC_SIGNATURE)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

    @Test
    void shouldRejectNotificationFromNonApprovedDomain() {
        given()
                .port(app.getLocalPort())
                .body(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION))
                .header("X-Forwarded-For", UNEXPECTED_IP_ADDRESS)
                .header("hmacSignature", VALID_HMAC_SIGNATURE)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void shouldRejectNotificationWhenHmacSignatureHeaderIsMissing() {
        given()
                .port(app.getLocalPort())
                .body(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION))
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
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
                .body(TestTemplateResourceLoader.load(ADYEN_TOKEN_CREATED_NOTIFICATION))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }
}
