package uk.gov.pay.connector.gatewayaccount.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_OK;

@Path("/")
@Tag(name = "Gateway accounts")
public class AdyenAccountSetupResource {
    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}/adyen-setup/{credentialId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve Adyen account setup tasks for a given gateway account ID, type and credential ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountSetup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response getAdyenAccountSetup(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "1", description = "Credential ID") @PathParam("credentialId") Long credentialId) {
        
        return Response.status(SC_OK).build();
    }
}
