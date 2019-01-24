package uk.gov.pay.connector.wallets.applepay;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

public interface WalletAuthorisationHandler {
    GatewayResponse<BaseAuthoriseResponse> authorise(WalletAuthorisationGatewayRequest request);
}
