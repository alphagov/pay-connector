package uk.gov.pay.connector.gatewayaccount.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import uk.gov.pay.connector.common.model.api.jsonpatch.JsonPatchRequest;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;
import uk.gov.pay.connector.gatewayaccount.resource.support.StripeAccountUtils;
import uk.gov.pay.connector.gatewayaccount.service.GatewayAccountService;
import uk.gov.pay.connector.gatewayaccount.service.StripeAccountSetupService;

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
    public StripeAccountSetup getStripeAccountSetup(@PathParam("accountId") Long accountId) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(StripeAccountUtils::isStripeGatewayAccount)
                .map(gatewayAccountEntity -> stripeAccountSetupService.getCompletedTasks(gatewayAccountEntity.getId()))
                .orElseThrow(NotFoundException::new);
    }

    @PATCH
    @Path("/v1/api/accounts/{accountId}/stripe-setup")
    @Consumes(APPLICATION_JSON)
    public Response patchStripeAccountSetup(@PathParam("accountId") Long accountId, JsonNode payload) {
        return gatewayAccountService.getGatewayAccount(accountId)
                .filter(StripeAccountUtils::isStripeGatewayAccount)
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
