package uk.gov.pay.connector.gatewayaccount.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdyenCredentialsTest {

    @Test
    void should_have_credentials_if_legal_entity_ID_is_not_null() {
        var adyenCredentialsWithCredentials = new AdyenCredentials("legal-entity-ID", null);

        assertTrue(adyenCredentialsWithCredentials.hasCredentials());
    }

    @Test
    void should_NOT_have_credentials_if_legal_entity_ID_is_null() {
        var adyenCredentialsWithoutCredentials = new AdyenCredentials(null, null);

        assertFalse(adyenCredentialsWithoutCredentials.hasCredentials());
    }
}
