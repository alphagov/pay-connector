package uk.gov.pay.connector.wallets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.ResponseUtil;
import uk.gov.pay.connector.wallets.googlepay.api.GenericGooglePayAuthRequest;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.Map;

import static uk.gov.pay.connector.util.ResponseUtil.authorisationRejectedResponse;
import static uk.gov.pay.connector.util.ResponseUtil.gatewayErrorResponse;
public class WalletService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WalletService.class);

    private WalletAuthoriseService authoriseService;

    private final ChargeService chargeService;

    @Inject
    public WalletService(WalletAuthoriseService authoriseService, ChargeService chargeService) {
        this.authoriseService = authoriseService;
        this.chargeService = chargeService;
    }

    public Response authorise(String chargeId, WalletAuthorisationRequest walletAuthorisationRequest) {
        LOGGER.info("Authorising {} charge with id {} ", walletAuthorisationRequest.getWalletType().toString(), chargeId);
        GatewayResponse<BaseAuthoriseResponse> response =
                authoriseService.authorise(chargeId, walletAuthorisationRequest);

        return response.getGatewayError()
                .map(error -> handleError(chargeId, error))
                .orElseGet(() -> response.getBaseResponse().map(this::handleAuthorisationResponse)
                        .orElseGet(() -> ResponseUtil.serviceErrorResponse("InterpretedStatus not found for Gateway response")));

    }

    public Response convertAndAuthorise(String chargeId, GenericGooglePayAuthRequest genericGooglePayAuthRequest) {
        ChargeEntity chargeEntity = chargeService.findChargeByExternalId(chargeId);
        switch(chargeEntity.getPaymentGatewayName()) {
            case WORLDPAY: 
                return authorise(chargeId, genericGooglePayAuthRequest.toWorldpayGooglePayAuthRequest());
            case STRIPE: 
                return authorise(chargeId, genericGooglePayAuthRequest.toStripeGooglePayAuthRequest());
            default: 
                throw new UnsupportedOperationException("Payment provider not supported");
        }
    }

    private Response handleAuthorisationResponse(BaseAuthoriseResponse baseAuthoriseResponse) {
        switch (baseAuthoriseResponse.authoriseStatus()) {
            case REJECTED:
                return authorisationRejectedResponse("This transaction was declined.");
            case ERROR:
            case EXCEPTION:
                return gatewayErrorResponse("There was an error authorising the transaction.");
            default:
                return ResponseUtil.successResponseWithEntity(Map.of("status", baseAuthoriseResponse.authoriseStatus().getMappedChargeStatus().toString()));
        }
    }

    protected Response handleError(String chargeId, GatewayError error) {
        switch (error.getErrorType()) {
            case GATEWAY_ERROR:
            case GATEWAY_CONNECTION_TIMEOUT_ERROR:
                return gatewayErrorResponse(error.getMessage());
            default:
                LOGGER.info("Charge {}: error {}", chargeId, error.getMessage());
                return gatewayErrorResponse(error.getMessage());
        }
    }

    
}
