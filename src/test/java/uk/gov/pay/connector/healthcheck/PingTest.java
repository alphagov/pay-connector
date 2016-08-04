package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PingTest {

    @Test
    public void testPing() {
        assertThat(new Ping().execute(), is(HealthCheck.Result.healthy()));
    }
}
