package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.inject.Inject;
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
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.AydenAccountSetupService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Tag(name = "Gateway accounts")
public class AdyenAccountSetupResource {
    private final GatewayAccountService gatewayAccountService;
    private final AydenAccountSetupService aydenAccountSetupService;

    @Inject
    public AdyenAccountSetupResource(GatewayAccountService gatewayAccountService, AydenAccountSetupService aydenAccountSetupService) {
        this.gatewayAccountService = gatewayAccountService;
        this.aydenAccountSetupService = aydenAccountSetupService;
    }

    @GET
    @Path("/v1/api/service/{serviceId}/account/{accountType}/adyen-setup/{credentialExternalId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve Adyen account setup tasks for a given service ID, account type and credential ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = AdyenAccountSetupResource.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public AdyenAccountSetupResponse getAdyenAccountSetup(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Credential External ID") @PathParam("credentialExternalId") String credentialExternalId ) { // pragma: allowlist secret

        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> { throw new GatewayAccountNotFoundException(serviceId, accountType); })
                .map(gatewayAccountEntity -> aydenAccountSetupService.getCompletedTasks(serviceId, gatewayAccountEntity.getId(), credentialExternalId))
                .orElseThrow(() -> new IllegalStateException("Internal Server Error"));
    }
}
