package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.inject.Inject;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountSetupService;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class StripeAccountSetupResource {
    private final StripeAccountSetupService stripeAccountSetupService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public StripeAccountSetupResource(StripeAccountSetupService stripeAccountSetupService,
                                      GatewayAccountService gatewayAccountService) {
        this.stripeAccountSetupService = stripeAccountSetupService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/stripe-setup")
    @Produces(APPLICATION_JSON)
    public StripeAccountSetup getStripeAccountSetup(@PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(gatewayAccountEntity -> PaymentGatewayName.STRIPE.getName().equals(gatewayAccountEntity.getGatewayName()))
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(NotFoundException::new);
    }
}
