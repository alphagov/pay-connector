package uk.gov.pay.connector.applepay;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.applepay.api.ApplePayToken;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.ResponseUtil;

import javax.inject.Inject;
import javax.ws.rs.core.Response;

import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.serviceErrorResponse;

public class ApplePayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplePayService.class);

    private ApplePayDecrypter applePayDecrypter;
    private AppleAuthoriseService authoriseService;

    @Inject
    public ApplePayService(ApplePayDecrypter applePayDecrypter, AppleAuthoriseService authoriseService) {
        this.applePayDecrypter = applePayDecrypter;
        this.authoriseService = authoriseService;
    }

    public Response authorise(String chargeId, ApplePayToken applePayToken) {
        LOGGER.info("Decrypting apple pay payload for charge with id {} ", chargeId);

        AppleDecryptedPaymentData data = applePayDecrypter.performDecryptOperation(applePayToken);
        data.setPaymentInfo(applePayToken.getApplePaymentInfo());

        LOGGER.info("Authorising apple pay charge with id {} ", chargeId);
        GatewayResponse<BaseAuthoriseResponse> response = authoriseService.doAuthorise(chargeId, data);
        if (isAuthorisationSubmitted(response)) {
            return badRequestResponse("This transaction was deferred.");
        }

        return isAuthorisationDeclined(response) ? badRequestResponse("This transaction was declined.") : handleGatewayAuthoriseResponse(response);
    }


    //PP-4314 These methods duplicated from the CardResource. This kind of handling shouldn't be there in the first place, so will refactor to be embedded in services rather than at resource level
    private Response handleGatewayAuthoriseResponse(GatewayResponse<? extends BaseAuthoriseResponse> response) {
        return response.getGatewayError()
                .map(this::handleError)
                .orElseGet(() -> response.getBaseResponse()
                        .map(r -> ResponseUtil.successResponseWithEntity(ImmutableMap.of("status", r.authoriseStatus().getMappedChargeStatus().toString())))
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));
    }
    
    private Response handleError(GatewayError error) {
        switch (error.getErrorType()) {
            case UNEXPECTED_HTTP_STATUS_CODE_FROM_GATEWAY:
            case MALFORMED_RESPONSE_RECEIVED_FROM_GATEWAY:
            case GATEWAY_URL_DNS_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
            case GATEWAY_CONNECTION_SOCKET_ERROR:
                return serviceErrorResponse(error.getMessage());
            default:
                return badRequestResponse(error.getMessage());
        }
    }
    
    private static boolean isAuthorisationSubmitted(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .filter(baseResponse -> baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.SUBMITTED)
                .isPresent();
    }

    private static boolean isAuthorisationDeclined(GatewayResponse<BaseAuthoriseResponse> response) {
        return response.getBaseResponse()
                .filter(baseResponse -> baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.REJECTED ||
                        baseResponse.authoriseStatus() == BaseAuthoriseResponse.AuthoriseStatus.ERROR)
                .isPresent();
    }
}
