package uk.gov.pay.connector.resources;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.jayway.jsonassert.JsonAssert;
import io.dropwizard.setup.Environment;
import org.hamcrest.core.Is;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckResourceTest {

    @Mock
    private Environment environment;

    @Mock
    private HealthCheckRegistry healthCheckRegistry;

    private HealthCheckResource resource;

    @Before
    public void setup() {
        when(environment.healthChecks()).thenReturn(healthCheckRegistry);
        resource = new HealthCheckResource(environment);
    }

    @Test
    public void checkHealthCheck_isUnHealthy() throws JsonProcessingException {
        SortedMap<String,HealthCheck.Result> registry = new TreeMap<>();
        registry.put("ping", HealthCheck.Result.unhealthy("application is unavailable"));
        registry.put("database", HealthCheck.Result.unhealthy("database in unavailable"));
        registry.put("deadlocks", HealthCheck.Result.unhealthy("no new threads available"));
        registry.put("cardExecutorService", HealthCheck.Result.unhealthy("thread pool exhausted"));
        when(healthCheckRegistry.runHealthChecks()).thenReturn(registry);
        Response response = resource.healthCheck();
        assertThat(response.getStatus(), is(503));
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String body = ow.writeValueAsString(response.getEntity());

        JsonAssert.with(body)
                .assertThat("$.*", hasSize(4))
                .assertThat("$.ping.healthy", Is.is(false))
                .assertThat("$.database.healthy", Is.is(false))
                .assertThat("$.cardExecutorService.healthy", Is.is(false))
                .assertThat("$.deadlocks.healthy", Is.is(false));
    }

    @Test
    public void checkHealthCheck_isHealthy() throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        SortedMap<String,HealthCheck.Result> registry = new TreeMap<>();
        registry.put("ping", HealthCheck.Result.healthy());
        registry.put("database", HealthCheck.Result.healthy());
        registry.put("deadlocks", HealthCheck.Result.healthy());
        registry.put("cardExecutorService", HealthCheck.Result.healthy());
        when(healthCheckRegistry.runHealthChecks()).thenReturn(registry);
        Response response = resource.healthCheck();
        assertThat(response.getStatus(), is(200));
        String body = ow.writeValueAsString(response.getEntity());

        JsonAssert.with(body)
                .assertThat("$.*", hasSize(4))
                .assertThat("$.ping.healthy", Is.is(true))
                .assertThat("$.database.healthy", Is.is(true))
                .assertThat("$.cardExecutorService.healthy", Is.is(true))
                .assertThat("$.deadlocks.healthy", Is.is(true));
    }

    @Test
    public void checkHealthCheck_AllHealthy_exceptDeadlocks() throws JsonProcessingException {
        SortedMap<String,HealthCheck.Result> registry = new TreeMap<>();
        registry.put("ping", HealthCheck.Result.healthy());
        registry.put("database", HealthCheck.Result.healthy());
        registry.put("deadlocks", HealthCheck.Result.unhealthy("no new threads available"));
        registry.put("cardExecutorService", HealthCheck.Result.healthy());
        when(healthCheckRegistry.runHealthChecks()).thenReturn(registry);
        Response response = resource.healthCheck();
        assertThat(response.getStatus(), is(503));
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String body = ow.writeValueAsString(response.getEntity());

        JsonAssert.with(body)
                .assertThat("$.*", hasSize(4))
                .assertThat("$.ping.healthy", Is.is(true))
                .assertThat("$.database.healthy", Is.is(true))
                .assertThat("$.deadlocks.healthy", Is.is(false))
                .assertThat("$.cardExecutorService.healthy", Is.is(true));
    }

    @Test
    public void checkHealthCheck_AllHealthy_exceptCardExecutorService() throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        SortedMap<String,HealthCheck.Result> registry = new TreeMap<>();
        registry.put("ping", HealthCheck.Result.healthy());
        registry.put("database", HealthCheck.Result.healthy());
        registry.put("deadlocks", HealthCheck.Result.healthy());
        Map<String, Integer> stats = new HashMap<>();
        stats.put("active-thread-count", 100);
        stats.put("pool", 200);
        stats.put("core-pool-size", 200);
        stats.put("queue-size", 12);
        registry.put("cardExecutorService", HealthCheck.Result.unhealthy(ow.writeValueAsString(stats)));
        when(healthCheckRegistry.runHealthChecks()).thenReturn(registry);
        Response response = resource.healthCheck();
        assertThat(response.getStatus(), is(503));
        String body = ow.writeValueAsString(response.getEntity());

        JsonAssert.with(body)
                .assertThat("$.*", hasSize(4))
                .assertThat("$.ping.healthy", Is.is(true))
                .assertThat("$.database.healthy", Is.is(true))
                .assertThat("$.cardExecutorService.healthy", Is.is(false))
                .assertThat("$.cardExecutorService.message", Is.is(ow.writeValueAsString(stats)))
                .assertThat("$.deadlocks.healthy", Is.is(true));
    }
}
