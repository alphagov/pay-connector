package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.Auth3dsResponseGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.AuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

public interface AuthorisationProvider<T extends BaseAuthoriseResponse> {
    PaymentGatewayName getPaymentGatewayName();
    
    Optional<String> generateTransactionId();

    GatewayResponse<T> authorise(AuthorisationGatewayRequest request);

    GatewayResponse<T> authorise3dsResponse(Auth3dsResponseGatewayRequest request);
}
