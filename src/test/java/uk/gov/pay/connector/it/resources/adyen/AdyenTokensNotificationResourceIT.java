package uk.gov.pay.connector.it.resources.adyen;

import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenRecurringTokenNotificationService;
import uk.gov.pay.connector.util.ConnectorAppWithCustomInjector;
import uk.gov.pay.connector.util.DnsPointerResourceRecord;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.util.Optional;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_XML;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.ConnectorModuleWithOverrides.reverseDnsLookup;

public class AdyenTokensNotificationResourceIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension(ConnectorAppWithCustomInjector.class);

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenRecurringTokenNotificationService.class);

    private static final String NOTIFICATION_PATH = "/v1/api/notifications/adyen/tokens";
    private static final String ADYEN_IP_ADDRESS = "192.168.0.1";
    private static final String UNEXPECTED_IP_ADDRESS = "8.8.8.8";
    private static final String HMAC_SIGNATURE = "hLz2zuhuylC8q36sCWWH7PpvbVpyaWDpoBqoEeTjj7w="; // pragma: allowlist secret
    private final String HMAC_SIGNATURE_FOR_PAYLOAD_WITH_UPDATED_TOKEN = "+309uQLT5A/L658R+4GlsOVwQ0rDTDcm2e5yln6+KGM="; // pragma: allowlist secret

    @BeforeAll
    static void before() {
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(ADYEN_IP_ADDRESS))).thenReturn(Optional.of(".adyen.com."));
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(UNEXPECTED_IP_ADDRESS))).thenReturn(Optional.of("dns.google."));
    }

    @Test
    void shouldHandleRecurringTokenNotification() {
        String payload = TestTemplateResourceLoader.load(TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION);

        given()
                .port(app.getLocalPort())
                .body(payload)
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", HMAC_SIGNATURE)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);
    }

    @Test
    void shouldRejectNotificationFromNonApprovedDomainForRecurringTokenNotification() {
        given()
                .port(app.getLocalPort())
                .body("{}")
                .header("X-Forwarded-For", UNEXPECTED_IP_ADDRESS)
                .header("hmacSignature", HMAC_SIGNATURE)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void shouldRejectNotificationWithInvalidHmacSignatureForRecurringTokenNotification() {
        String payload = TestTemplateResourceLoader.load(TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION);

        given()
                .port(app.getLocalPort())
                .body(payload)
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", "some invalid Hmac Signature")
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);

        logs.assertContains("Hmac signature is invalid, rejecting Adyen token notification");
    }

    @Test
    void shouldRejectUnsupportedHttpMethodForRecurringTokenNotification() {
        given()
                .port(app.getLocalPort())
                .get(NOTIFICATION_PATH)
                .then()
                .statusCode(405);
    }

    @Test
    void shouldRejectUnsupportedContentTypeForRecurringTokenNotification() {
        given()
                .port(app.getLocalPort())
                .body("{}")
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", HMAC_SIGNATURE)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    @Test
    void shouldIgnoreUnsupportedRecurringTokenNotification() {
        String payload = TestTemplateResourceLoader.load(
                TestTemplateResourceLoader.ADYEN_TOKEN_NOTIFICATION
        ).replace(
                "\"type\": \"recurring.token.created\"",
                "\"type\": \"recurring.token.updated\""
        );
        
        given()
                .port(app.getLocalPort())
                .body(payload)
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", HMAC_SIGNATURE_FOR_PAYLOAD_WITH_UPDATED_TOKEN)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(200);

        logs.assertContains(
                "Ignoring unsupported Adyen token notification"
        );
        
    }
    @Test
    void shouldReturn500WhenRecurringTokenNotificationCannotBeDeserialised() {
        given()
                .port(app.getLocalPort())
                .body("invalidJson")
                .header("X-Forwarded-For", ADYEN_IP_ADDRESS)
                .header("hmacSignature", HMAC_SIGNATURE)
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(500);

        logs.assertContains("Error deserialising token notification payload");
    }
}
