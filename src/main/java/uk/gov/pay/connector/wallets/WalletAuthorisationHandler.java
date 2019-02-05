package uk.gov.pay.connector.wallets;

import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

public interface WalletAuthorisationHandler {
    GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request) 
            throws GenericGatewayErrorException, GatewayConnectionErrorException, GatewayConnectionTimeoutErrorException;
}
