package uk.gov.pay.connector.healthcheck;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PingTest {

    @Test
    void testPing() {
        assertTrue(new Ping().execute().isHealthy());
    }
}
