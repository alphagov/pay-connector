package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public class AuthorisationResponse implements GatewayResponse {

    private final Boolean successful;
    private final GatewayError error;
    private final ChargeStatus status;

    public AuthorisationResponse(Boolean successful, GatewayError error, ChargeStatus status) {
        this.successful = successful;
        this.error = error;
        this.status = status;
    }

    public static AuthorisationResponse successfulAuthorisation(ChargeStatus status) {
        return new AuthorisationResponse(true, null, status);
    }

    public static AuthorisationResponse anErrorResponse(GatewayError errorMessage) {
        return new AuthorisationResponse(false, errorMessage, null);
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public GatewayError getError() {
        return error;
    }

    public ChargeStatus getNewChargeStatus() {
        return status;
    }
}
