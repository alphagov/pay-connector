package uk.gov.pay.connector.paymentprocessor.resource;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.paymentprocessor.service.DiscrepancyService;
import uk.gov.pay.connector.paymentprocessor.model.GatewayStatusComparison;

import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Tag(name = "Discrepancies")
public class DiscrepancyResource {

    private final DiscrepancyService discrepancyService;

    @Inject
    public DiscrepancyResource(DiscrepancyService discrepancyService) {
        this.discrepancyService = discrepancyService;
    }

    @POST
    @Path("/v1/api/discrepancies/report")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Compare charge status with gateway",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GatewayStatusComparison.class)))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public List<GatewayStatusComparison> listDiscrepancies(
            @Parameter(array = @ArraySchema(schema = @Schema(implementation = String.class, example = "charge-external-id")))
            @NotEmpty List<String> chargeIds) {
        return discrepancyService.listGatewayStatusComparisons(chargeIds);
    }

    @POST
    @Path("/v1/api/discrepancies/resolve")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Resolve charge status discrepancy",
            description = "When charge status mismatches with Gateway and is in cancellable state, charge is cancelled. Otherwise no action takes place",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = GatewayStatusComparison.class)))),
                    @ApiResponse(responseCode = "500", description = "Internal server error")
            }
    )
    public List<GatewayStatusComparison> resolveDiscrepancies(
            @Parameter(array = @ArraySchema(schema = @Schema(implementation = String.class, example = "charge-external-id")))
            @NotEmpty List<String> chargeIds) {
        return discrepancyService.resolveDiscrepancies(chargeIds);
    }
}
