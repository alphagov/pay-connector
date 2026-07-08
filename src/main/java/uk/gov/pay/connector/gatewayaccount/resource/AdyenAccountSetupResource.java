package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
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

    @PATCH
    @Path("/v1/api/service/{serviceId}/account/{accountType}/adyen-setup/{credentialExternalId}")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Update Adyen account setup tasks for a given service ID, account type and credential ID",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"bank_account\"," +
                    "        \"value\": COMPLETED" +
                    "    }," +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"responsible_person\"," +
                    "        \"value\": NOT_STARTED" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Not found"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
            }
    )
    public Response patchAdyenAccountSetup(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, // pragma: allowlist secret
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Credential External ID") @PathParam("credentialExternalId") String credentialExternalId ) { // pragma: allowlist secret
    
        return Response.ok().build();
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
