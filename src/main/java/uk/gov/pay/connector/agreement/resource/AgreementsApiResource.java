package uk.gov.pay.connector.agreement.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.agreement.model.AgreementCancelRequest;
import uk.gov.pay.connector.agreement.model.AgreementCreateRequest;
import uk.gov.pay.connector.agreement.model.AgreementResponse;
import uk.gov.pay.connector.agreement.service.AgreementService;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static org.apache.http.HttpStatus.SC_CREATED;

@Path("/")
@Tag(name = "Agreements")
public class AgreementsApiResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgreementsApiResource.class);

    private final AgreementService agreementService;

    @Inject
    public AgreementsApiResource(AgreementService agreementService) {
        this.agreementService = agreementService;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/agreements")
    @Produces("application/json")
    @Consumes("application/json")
    @Operation(
            summary = "Create an agreement",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AgreementResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Missing required fields", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response createAgreement(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId,
            @Valid AgreementCreateRequest agreementCreateRequest
    ) {
        LOGGER.info("Creating new agreement for gateway account ID {}", accountId);
        AgreementResponse agreementResponse = agreementService.createByGatewayAccountId(agreementCreateRequest, accountId).orElseThrow(NotFoundException::new);
        return Response.status(SC_CREATED).entity(agreementResponse).build();
    }

    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/agreements")
    @Produces("application/json")
    @Consumes("application/json")
    @Operation(
            summary = "Create an agreement for a service ID and account type (test|live)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AgreementResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Missing required fields", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found")
            }
    )
    public Response createAgreement(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @Valid AgreementCreateRequest agreementCreateRequest
    ) {
        LOGGER.info("Creating new agreement for service ID {}, account type {}", serviceId, accountType);
        AgreementResponse agreementResponse = agreementService.createByServiceIdAndAccountType(agreementCreateRequest, serviceId, accountType).orElseThrow(NotFoundException::new);
        return Response.status(SC_CREATED).entity(agreementResponse).build();
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/agreements/{agreementId}/cancel")
    @Consumes("application/json")
    public Response cancelAgreement(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId,
            @PathParam("agreementId") String agreementId,
            @Valid AgreementCancelRequest agreementCancelRequest
    ) {
        agreementService.cancelByGatewayAccountId(agreementId, accountId, agreementCancelRequest);
        return Response.noContent().build();
    }

    @POST
    @Path("/v1/api/service/{serviceId}/account/{accountType}/agreements/{agreementId}/cancel")
    @Consumes("application/json")
    public Response cancelAgreement(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType,
            @PathParam("agreementId") String agreementId,
            @Valid AgreementCancelRequest agreementCancelRequest
    ) {
        agreementService.cancelByServiceIdAndAccountType(agreementId, serviceId, accountType, agreementCancelRequest);
        return Response.noContent().build();
    }
    
    
}
