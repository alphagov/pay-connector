package uk.gov.pay.connector.gatewayaccount.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountResponse;
import uk.gov.pay.connector.gatewayaccount.resource.support.StripeAccountUtils;
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
                .filter(StripeAccountUtils::isStripeGatewayAccount)
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(NotFoundException::new);
    }

}
