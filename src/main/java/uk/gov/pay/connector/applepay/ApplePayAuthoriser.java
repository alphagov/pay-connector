package uk.gov.pay.connector.applepay;

import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

public interface ApplePayAuthoriser {
    GatewayResponse<BaseAuthoriseResponse> authorise(ApplePayAuthorisationGatewayRequest request);

    Optional<String> generateTransactionId();
}
