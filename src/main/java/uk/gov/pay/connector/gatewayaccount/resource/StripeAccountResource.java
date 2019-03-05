package uk.gov.pay.connector.gatewayaccount.resource;

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
    public StripeAccountResponse getStripeAccount(@PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(StripeAccountUtils::isStripeGatewayAccount)
                .flatMap(stripeAccountService::buildStripeAccountResponse)
                .orElseThrow(NotFoundException::new);
    }

}
