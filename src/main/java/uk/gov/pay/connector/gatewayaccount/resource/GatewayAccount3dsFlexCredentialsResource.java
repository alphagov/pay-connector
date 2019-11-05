package uk.gov.pay.connector.gatewayaccount.resource;

import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayUpdate3dsFlexCredentialsRequest;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.Worldpay3dsFlexCredentialsService;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static uk.gov.pay.connector.util.ResponseUtil.notFoundResponse;

@Path("/")
public class GatewayAccount3dsFlexCredentialsResource {

    private final GatewayAccountService gatewayAccountService;
    private final Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService;

    @Inject
    public GatewayAccount3dsFlexCredentialsResource(GatewayAccountService gatewayAccountService,
                                                    Worldpay3dsFlexCredentialsService worldpay3dsFlexCredentialsService) {
        this.gatewayAccountService = gatewayAccountService;
        this.worldpay3dsFlexCredentialsService = worldpay3dsFlexCredentialsService;
    }

    @POST
    @Path("/v1/api/accounts/{accountId}/3ds-flex-credentials")
    @Produces(APPLICATION_JSON)
    @Consumes(APPLICATION_JSON)
    public Response createOrUpdateWorldpay3dsCredentials(@PathParam("accountId") Long gatewayAccountId,
                                                         @Valid WorldpayUpdate3dsFlexCredentialsRequest worldpay3dsCredentials) {

        return gatewayAccountService.getGatewayAccount(gatewayAccountId)
                .filter(gatewayAccountEntity ->
                        gatewayAccountEntity.getGatewayName().equals(PaymentGatewayName.WORLDPAY.getName()))
                .map(gatewayAccountEntity -> {
                    worldpay3dsFlexCredentialsService.setGatewayAccountWorldpay3dsFlexCredentials(worldpay3dsCredentials,
                            gatewayAccountEntity);
                    return Response.ok().build();
                })
                .orElseGet(() -> notFoundResponse("Not a Worldpay gateway account"));
    }
}
