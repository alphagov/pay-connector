package uk.gov.pay.connector.charge.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.charge.service.AuthorisationErrorGatewayCleanupService;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.successResponseWithEntity;

@Path("/")
@Tag(name = "Tasks")
public class GatewayCleanupResource {

    private AuthorisationErrorGatewayCleanupService cleanupService;

    @Inject
    public GatewayCleanupResource(AuthorisationErrorGatewayCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }
    @POST
    @Path("/v1/tasks/gateway-cleanup-sweep")
    @Operation(
            summary = "Cleanup charges with Gateway",
            description = "Finds all charges (ePDQ, Worldpay, Stripe) which have a status of AUTHORISATION ERROR, AUTHORISATION UNEXPECTED ERROR, AUTHORISATION TIMEOUT " +
                    "and checks what their status is with the payment gateway. If the charges exist on the gateway and are in a non-terminal state, " +
                    "e.g. AUTHORISATION SUCCESS, a request is sent to cancel the charge on the gateway.<br>" +
                    "The job will move the charge into one of three statuses when it has successfully handled it:<br>" +
                    " - AUTHORISATION ERROR CANCELLED - the charge was authorised on the gateway but has now been cancelled. <br>" +
                    " - AUTHORISATION ERROR REJECTED - the authorisation was rejected on the gateway and no action needed to be taken to clean up.<br>" +
                    " - AUTHORISATION ERROR CHARGE MISSING - the charge was not found on the gateway, most likely because the error was before the gateway processed the authorisation.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(example = "{" +
                                    "\"cleanup-success\": 90," +
                                    "\"cleanup-failed\": 10" +
                                    "}"))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    @Produces(APPLICATION_JSON)
    public Response cleanupChargesInAuthErrorWithGateway(
            @Parameter(example = "100", description = "The maximum number of charges in an error state that will be processed by the task")
            @QueryParam("limit") @NotNull(message = "Parameter [limit] is required") Integer limit) {
        Map<String, Integer> resultMap = cleanupService.sweepAndCleanupAuthorisationErrors(limit);
        return successResponseWithEntity(resultMap);
    }
}
