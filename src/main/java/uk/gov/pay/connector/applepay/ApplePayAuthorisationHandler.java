package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

public interface ApplePayAuthorisationHandler {
    GatewayResponse<BaseAuthoriseResponse> authorise(ApplePayAuthorisationGatewayRequest request);
}
