package uk.gov.pay.connector.gatewayaccount.resource;

import com.google.inject.Inject;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetup;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountStripeSetupService;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/")
public class GatewayAccountStripeSetupResource {
    private final GatewayAccountStripeSetupService gatewayAccountStripeSetupService;
    private final GatewayAccountService gatewayAccountService;

    @Inject
    public GatewayAccountStripeSetupResource(GatewayAccountStripeSetupService gatewayAccountStripeSetupService,
                                             GatewayAccountService gatewayAccountService) {
        this.gatewayAccountStripeSetupService = gatewayAccountStripeSetupService;
        this.gatewayAccountService = gatewayAccountService;
    }

    @GET
    @Path("/v1/api/accounts/{accountId}/stripe-setup")
    @Produces(APPLICATION_JSON)
    public GatewayAccountStripeSetup getGatewayAccountStripeSetup(@PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(gatewayAccountEntity -> PaymentGatewayName.STRIPE.getName().equals(gatewayAccountEntity.getGatewayName()))
                .map(gatewayAccountEntity -> gatewayAccountStripeSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(NotFoundException::new);
    }
}
