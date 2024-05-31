package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountSetupService;
import uk.gov.service.payments.commons.model.jsonpatch.JsonPatchRequest;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Tag(name = "Gateway accounts")
public class StripeAccountSetupResource {
    private final StripeAccountSetupService stripeAccountSetupService;
    private final GatewayAccountService gatewayAccountService;
    private final StripeAccountSetupRequestValidator stripeAccountSetupRequestValidator;

    @Inject
    public StripeAccountSetupResource(StripeAccountSetupService stripeAccountSetupService,
                                      GatewayAccountService gatewayAccountService,
                                      StripeAccountSetupRequestValidator stripeAccountSetupRequestValidator) {
        this.stripeAccountSetupService = stripeAccountSetupService;
        this.gatewayAccountService = gatewayAccountService;
        this.stripeAccountSetupRequestValidator = stripeAccountSetupRequestValidator;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/stripe-setup")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve Stripe connect account setup tasks for a given gateway account ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountSetup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public StripeAccountSetup getStripeAccountSetup(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/v1/api/service/{serviceId}/{accountType}/stripe-setup")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieve Stripe connect account setup tasks for a given service ID and account type",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountSetup.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public StripeAccountSetup getStripeAccountSetupByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId, 
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(() -> new GatewayAccountNotFoundException(serviceId, accountType));
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}/stripe-setup")
    @Consumes(APPLICATION_JSON)
    @Operation(
            summary = "Update Stripe Connect account setup tasks have been completed for a given accountId",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"bank_account\"," +
                    "        \"value\": true" +
                    "    }," +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"responsible_person\"," +
                    "        \"value\": false" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request - operation not allowed"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response patchStripeAccountSetup(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId, JsonNode payload) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .map(gatewayAccountEntity -> {
                    stripeAccountSetupRequestValidator.validatePatchRequest(payload);
                    List<StripeAccountSetupUpdateRequest> updateRequests = StreamSupport
                            .stream(payload.spliterator(), false)
                            .map(JsonPatchRequest::from)
                            .map(StripeAccountSetupUpdateRequest::from)
                            .collect(Collectors.toList());

                    stripeAccountSetupService.update(gatewayAccountEntity, updateRequests);
                    return Response.ok().build();
                }).orElseThrow(NotFoundException::new);
    }

    @PATCH
    @Path("/v1/api/service/{serviceId}/account/{accountType}/stripe-setup")
    @Consumes(APPLICATION_JSON)
    @Operation(
            summary = "Update Stripe Connect account setup tasks have been completed for a given service ID and account type",
            description = "Support patching following paths: <br>" +
                    "bank_account, responsible_person, vat_number, company_number, director, government_entity_document, organisation_details",
            requestBody = @RequestBody(content = @Content(schema = @Schema(example = "[" +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"bank_account\"," +
                    "        \"value\": true" +
                    "    }," +
                    "    {" +
                    "        \"op\": \"replace\"," +
                    "        \"path\": \"responsible_person\"," +
                    "        \"value\": false" +
                    "    }" +
                    "]"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request - operation not allowed"),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response patchStripeAccountSetupByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            JsonNode payload) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .map(gatewayAccountEntity -> {
                    stripeAccountSetupRequestValidator.validatePatchRequest(payload);
                    List<StripeAccountSetupUpdateRequest> updateRequests = StreamSupport
                            .stream(payload.spliterator(), false)
                            .map(JsonPatchRequest::from)
                            .map(StripeAccountSetupUpdateRequest::from)
                            .collect(Collectors.toList());

                    stripeAccountSetupService.update(gatewayAccountEntity, updateRequests);
                    return Response.ok().build();
                }).orElseThrow(NotFoundException::new);
    }

}
