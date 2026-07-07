package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.service.AdyenAccountSetupService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

@Path("/")
@Tag(name = "Gateway accounts")
public class AdyenAccountSetupResource {
    private final GatewayAccountService gatewayAccountService;
    private final AdyenAccountSetupService adyenAccountSetupService;

    @Inject
    public AdyenAccountSetupResource(GatewayAccountService gatewayAccountService, AdyenAccountSetupService adyenAccountSetupService) {
        this.gatewayAccountService = gatewayAccountService;
        this.adyenAccountSetupService = adyenAccountSetupService;
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

        var gatewayAccountEntity = gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));

        var gatewayAccountCredentialsEntity = validateGatewayAccountCredentialsEntity(credentialExternalId, gatewayAccountEntity);

        return adyenAccountSetupService.buildResponse(serviceId, gatewayAccountEntity.getId(), gatewayAccountCredentialsEntity);
    }

    private GatewayAccountCredentialsEntity validateGatewayAccountCredentialsEntity(String credentialExternalId, GatewayAccountEntity gatewayAccountEntity) {
        var gatewayAccountCredentialsEntity = gatewayAccountEntity.getGatewayAccountCredentials().stream().filter(credentialsEntity ->
                        credentialsEntity.getExternalId().equals(credentialExternalId)).findFirst()
                .orElseThrow(NotFoundException::new);

        if (!gatewayAccountCredentialsEntity.getPaymentProvider().equals(ADYEN.getName())) {
            throw new NotFoundException("Credential is not associated with payment provider Adyen");
        }

        return gatewayAccountCredentialsEntity;
    }
}
