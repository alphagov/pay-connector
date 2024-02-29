package uk.gov.pay.connector.paymentprocessor.api;

import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

public class AuthorisationResponse {
    private final Optional<BaseAuthoriseResponse.AuthoriseStatus> authoriseStatus;
    private final Optional<GatewayError> gatewayError;

    public AuthorisationResponse(GatewayResponse<BaseAuthoriseResponse> gatewayResponse) {
        authoriseStatus = gatewayResponse.getBaseResponse().map(BaseAuthoriseResponse::authoriseStatus);
        gatewayError = gatewayResponse.getGatewayError();
    }

    public Optional<GatewayError> getGatewayError() {
        return gatewayError;
    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return authoriseStatus;
    }
}
