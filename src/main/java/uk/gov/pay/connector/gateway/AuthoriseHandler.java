package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

public interface AuthoriseHandler {

    GatewayResponse authorise(CardAuthorisationGatewayRequest request);
}
