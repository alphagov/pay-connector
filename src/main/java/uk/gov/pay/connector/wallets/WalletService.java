package uk.gov.pay.connector.wallets;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.gatewayErrorResponse;

public class WalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletService.class);

    private WalletAuthoriseService authoriseService;

    @Inject
    public WalletService(WalletAuthoriseService authoriseService) {
        this.authoriseService = authoriseService;
    }

    public Response authorise(String chargeId, WalletAuthorisationRequest walletAuthorisationRequest) {
        LOGGER.info("Authorising {} charge with id {} ", walletAuthorisationRequest.getWalletType().toString(), chargeId);
        GatewayResponse<BaseAuthoriseResponse> response =
                authoriseService.doAuthorise(chargeId, walletAuthorisationRequest);

        if (isAuthorisationSubmitted(response)) {
            LOGGER.info("Charge {}: {} authorisation was deferred.", chargeId, walletAuthorisationRequest.getWalletType().toString());
            return badRequestResponse("This transaction was deferred.");
        }
        return isAuthorisationDeclined(response) ? badRequestResponse("This transaction was declined.") : handleGatewayAuthoriseResponse(chargeId, response);
    }
    
    //PP-4314 These methods duplicated from the CardResource. This kind of handling shouldn't be there in the first place, so will refactor to be embedded in services rather than at resource level
    protected Response handleGatewayAuthoriseResponse(String chargeId, GatewayResponse<? extends BaseAuthoriseResponse> response) {
        return response.getGatewayError()
                .map(error -> handleError(chargeId, error))
                .orElseGet(() -> response.getBaseResponse()
                        .map(r -> ResponseUtil.successResponseWithEntity(ImmutableMap.of("status", r.authoriseStatus().getMappedChargeStatus().toString())))
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }

    protected Response handleError(String chargeId, GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return gatewayErrorResponse(error.getMessage());
            default:
                LOGGER.info("Charge {}: error {}", chargeId, error.getMessage());
                return badRequestResponse(error.getMessage());
        }
    }

    protected static boolean isAuthorisationSubmitted(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .filter(baseResponse -> baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.SUBMITTED)
                .isPresent();
    }

    protected static boolean isAuthorisationDeclined(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .filter(baseResponse -> baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.REJECTED ||
                        baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.ERROR)
                .isPresent();
    }
}
