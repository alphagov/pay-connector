package uk.gov.pay.connector.wallets;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.paymentprocessor.api.AuthorisationResponse;
import uk.gov.pay.connector.util.ResponseUtil;
import uk.gov.pay.connector.wallets.model.StripeWalletAuthorisationRequest;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

@Path("/")
public class StripeWalletResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeWalletResource.class);
    
    private final StripeWalletService stripeWalletService;

    @Inject
    public StripeWalletResource(StripeWalletService stripeWalletService) {
        this.stripeWalletService = stripeWalletService;
    }

    @POST
    @Path("/v1/frontend/charges/{chargeId}/stripe/wallets")
    public Response authoriseWalletCharge(@PathParam("chargeId") String chargeId,
                                          StripeWalletAuthorisationRequest stripeWalletAuthorisationRequest) {
        AuthorisationResponse response = stripeWalletService.authorise(chargeId, stripeWalletAuthorisationRequest);
        if (response.getAuthoriseStatus()
                .filter(authoriseStatus -> authoriseStatus == BaseAuthoriseResponse.AuthoriseStatus.REJECTED ||
                authoriseStatus == BaseAuthoriseResponse.AuthoriseStatus.ERROR)
                .isPresent()) {
            return badRequestResponse("This transaction was declined.");
        }

        return response.getGatewayError()
                .map(error -> handleError(chargeId, error))
                .orElseGet(() -> response.getAuthoriseStatus()
                        .map(authoriseStatus -> ResponseUtil.successResponseWithEntity(ImmutableMap.of("status", authoriseStatus.getMappedChargeStatus().toString())))
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    protected Response handleError(String chargeId, GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return serviceErrorResponse(error.getMessage());
            default:
                LOGGER.error("Charge {}: error {}", chargeId, error.getMessage());
                return badRequestResponse(error.getMessage());
        }
    }
}
