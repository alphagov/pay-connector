package uk.gov.pay.connector.paymentprocessor.model;

import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.paymentprocessor.service.PaymentProviderAuthorisationResponse;

import java.util.Optional;

public class AuthorisationResponse {
    private BaseAuthoriseResponse.AuthoriseStatus authoriseStatus;
    private GatewayError gatewayError;

    public AuthorisationResponse(PaymentProviderAuthorisationResponse gatewayResponse) {
        gatewayResponse.getAuthoriseStatus().ifPresent(status -> authoriseStatus = status);
        gatewayResponse.getGatewayError().ifPresent(error -> gatewayError = error);
    }

    public Optional<GatewayError> getGatewayError() {
        return Optional.ofNullable(gatewayError);
    }

    public Optional<BaseAuthoriseResponse.AuthoriseStatus> getAuthoriseStatus() {
        return Optional.ofNullable(authoriseStatus);
    }
}
