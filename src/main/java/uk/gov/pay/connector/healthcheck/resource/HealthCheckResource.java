package uk.gov.pay.connector.healthcheck.resource;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.dropwizard.core.setup.Environment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.commons.lang3.StringUtils.defaultString;

@Path("/")
public class HealthCheckResource {

    private final Environment environment;
    private final Logger logger = LoggerFactory.getLogger(HealthCheckResource.class);

    @Inject
    public HealthCheckResource(Environment environment) {
        this.environment = environment;
    }

    @GET
    @Path("healthcheck")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Healthcheck endpoint for connector (checks postgresql, cardExecutorService, ping, sqsQueue, deadlocks)",
            tags = {"Other"},
            responses = {@ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(example = "{" +
                    "    \"database\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"cardExecutorService\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"ping\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"sqsQueue\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }," +
                    "    \"deadlocks\": {" +
                    "        \"healthy\": true," +
                    "        \"message\": \"Healthy\"" +
                    "    }" +
                    "}")
            )),
                    @ApiResponse(responseCode = "503", description = "Service Unavailable")
            }
    )
    public Response healthCheck() {
        SortedMap<String, HealthCheck.Result> results = environment.healthChecks().runHealthChecks();

        Map<String, Map<String, Object>> response = results.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                healthCheck -> ImmutableMap.of(
                                        "healthy", healthCheck.getValue().isHealthy(),
                                        "message", Objects.toString(healthCheck.getValue().getMessage(), "Healthy"))
                        )
                );

        if (allHealthy(results.values())) {
            return Response.ok(response).build();
        }
        logger.error("Healthcheck Failure: {}", response);
        return Response.status(503).entity(response).build();
    }

    private boolean allHealthy(Collection<HealthCheck.Result> results) {
        return results.stream().allMatch(HealthCheck.Result::isHealthy);
    }
}
