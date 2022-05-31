package uk.gov.pay.connector.charge.resource;

import uk.gov.pay.connector.charge.service.AuthorisationErrorGatewayCleanupService;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
public class GatewayCleanupResource {

    private AuthorisationErrorGatewayCleanupService cleanupService;

    @Inject 
    public GatewayCleanupResource(AuthorisationErrorGatewayCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @POST
    @Path("/v1/tasks/gateway-cleanup-sweep")
    @Produces(APPLICATION_JSON)
    public Response cleanupChargesInAuthErrorWithGateway(@QueryParam("limit") @NotNull(message = "Parameter [limit] is required") Integer limit) {
        Map<String, Integer> resultMap = cleanupService.sweepAndCleanupAuthorisationErrors(limit);
        return successResponseWithEntity(resultMap);
    }
}
