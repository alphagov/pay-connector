package uk.gov.pay.connector.healthcheck;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.ResourceTestRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.healthcheck.resource.HealthCheckResource;
import uk.gov.pay.connector.rules.ResourceTestRuleWithCustomExceptionMappersBuilder;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckResourceTest {

    private static Environment environment = mock(Environment.class);
    private static HealthCheckRegistry healthCheckRegistry = mock(HealthCheckRegistry.class);

    static {
        when(environment.healthChecks()).thenReturn(healthCheckRegistry);
    }

    @ClassRule
    public static ResourceTestRule resource = ResourceTestRuleWithCustomExceptionMappersBuilder.getBuilder()
            .addResource(new HealthCheckResource(environment))
            .build();

    @Test
    public void shoudReturn200WhenHealthy() {
        SortedMap<String, HealthCheck.Result> healthcheckResults = new TreeMap<>(Map.of(
                "check", HealthCheck.Result.healthy()
        ));

        when(healthCheckRegistry.runHealthChecks()).thenReturn(healthcheckResults);
        Response response = resource.client()
                .target("/healthcheck")
                .request()
                .get();
        assertThat(response.getStatus(), is(200));
    }

    @Test
    public void shoudReturn503WhenAtLeastOneUnhealthyResult() {
        SortedMap<String, HealthCheck.Result> healthcheckResults = new TreeMap<>(Map.of(
                "check1", HealthCheck.Result.unhealthy("borked"),
                "check2", HealthCheck.Result.healthy()
        ));

        when(healthCheckRegistry.runHealthChecks()).thenReturn(healthcheckResults);
        Response response = resource.client()
                .target("/healthcheck")
                .request()
                .get();
        assertThat(response.getStatus(), is(503));
    }
}
