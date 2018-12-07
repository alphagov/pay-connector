package uk.gov.pay.connector.paymentprocessor.api;

import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import java.util.Optional;

public class AuthorisationResponse {
    private BaseAuthoriseResponse.AuthoriseStatus authoriseStatus;
    private GatewayError gatewayError;

    public AuthorisationResponse(GatewayResponse<BaseAuthoriseResponse> gatewayResponse) {
        gatewayResponse.getBaseResponse().ifPresent(baseAuthoriseResponse -> authoriseStatus = baseAuthoriseResponse.authoriseStatus());
        gatewayResponse.getGatewayError().ifPresent(error -> gatewayError = error);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(gatewayError);
    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(authoriseStatus);
    }
}
