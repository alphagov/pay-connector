package uk.gov.pay.connector.app.adyen;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookHmacKeysTest {
    @Test
    void getPrimary_returnsValue_whenNonBlank() {
        WebhookHmacKeys keys = new WebhookHmacKeys("primary-key", null);
        Optional<String> result = keys.getPrimary();

        assertEquals("primary-key", result.get());
    }

    @Test
    void getPrimary_returnsEmpty_whenNull() {
        WebhookHmacKeys keys = new WebhookHmacKeys(null, null);

        Optional<String> result = keys.getPrimary();
        assertTrue(result.isEmpty());
    }

    @Test
    void getPrimary_returnsEmpty_whenBlank() {
        WebhookHmacKeys keys = new WebhookHmacKeys("   ", null);

        Optional<String> result = keys.getPrimary();
        assertTrue(result.isEmpty());
    }

    @Test
    void getSecondary_returnsValue_whenNonBlank() {
        WebhookHmacKeys keys = new WebhookHmacKeys(null, "secondary-key");
        Optional<String> result = keys.getSecondary();

        assertEquals("secondary-key", result.get());
    }

    @Test
    void getSecondary_returnsEmpty_whenNull() {
        WebhookHmacKeys keys = new WebhookHmacKeys(null, null);
        Optional<String> result = keys.getSecondary();

        assertTrue(result.isEmpty());
    }

    @Test
    void getSecondary_returnsEmpty_whenBlank() {
        WebhookHmacKeys keys = new WebhookHmacKeys(null, "   ");
        Optional<String> result = keys.getSecondary();

        assertTrue(result.isEmpty());
    }

    @Test
    void bothValues_workIndependently() {
        WebhookHmacKeys keys = new WebhookHmacKeys("primaryKey", "secondaryKey");

        assertEquals("primaryKey", keys.getPrimary().get());
        assertEquals("secondaryKey", keys.getSecondary().get());
    }
}
