package uk.gov.pay.connector.healthcheck;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PingTest {

    @Test
    public void testPing() {
        assertTrue(new Ping().execute().isHealthy());
    }
}
