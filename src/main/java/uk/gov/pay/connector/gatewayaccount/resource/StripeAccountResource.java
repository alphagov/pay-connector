package uk.gov.pay.connector.gatewayaccount.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.gatewayaccount.exception.GatewayAccountNotFoundException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountService;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
@Tag(name = "Gateway accounts")
public class StripeAccountResource {

    private final StripeAccountService stripeAccountService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public StripeAccountResource(StripeAccountService stripeAccountService,
                                 GatewayAccountService gatewayAccountService) {
        this.stripeAccountService = stripeAccountService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/stripe-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieves Stripe Connect account information for a given gateway account ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - Account does not exist or not a stripe gateway account or account does not have Stripe credentials, ")
            }
    )
    public StripeAccountResponse getStripeAccount(
            @Parameter(example = "1", description = "Gateway account ID")
            @PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(GatewayAccountEntity::isStripeGatewayAccount)
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(NotFoundException::new);
    }

    @GET
    @Path("/v1/api/service/{serviceId}/{accountType}/stripe-account")
    @Produces(APPLICATION_JSON)
    @Operation(
            summary = "Retrieves Stripe Connect account information for a given service ID and account type (test|live)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = StripeAccountResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not found - Service does not exist or service does not have a Stripe gateway account of this type")
            }
    )
    public StripeAccountResponse getStripeAccountByServiceIdAndAccountType(
            @Parameter(example = "46eb1b601348499196c99de90482ee68", description = "Service ID") @PathParam("serviceId") String serviceId,
            @Parameter(example = "test", description = "Account type") @PathParam("accountType") GatewayAccountType accountType) {
        return gatewayAccountService.getGatewayAccountByServiceIdAndAccountType(serviceId, accountType)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(serviceId, accountType);
                })
                .filter(GatewayAccountEntity::isStripeGatewayAccount)
                .or(() -> {
                    throw new GatewayAccountNotFoundException(String.format("Gateway account for service ID [%s] and account type [%s] is not a Stripe account", serviceId, accountType));
                })
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(() -> new NotFoundException(String.format("Stripe gateway account for service ID [%s] and account type [%s] does not have Stripe credentials", serviceId, accountType)));
    }

}
