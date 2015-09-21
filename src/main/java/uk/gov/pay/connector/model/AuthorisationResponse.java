package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;

public class AuthorisationResponse implements GatewayResponse {

    private final Boolean successful;
    private final GatewayError error;
    private final ChargeStatus status;
    private String transactionId;

    public AuthorisationResponse(Boolean successful, GatewayError error, ChargeStatus status, String transactionId) {
        this.successful = successful;
        this.error = error;
        this.status = status;
        this.transactionId = transactionId;
    }

    public static AuthorisationResponse successfulAuthorisation(ChargeStatus status, String transactionId) {
        return new AuthorisationResponse(true, null, status, transactionId);
    }

    public static AuthorisationResponse anErrorResponse(GatewayError errorMessage, String transactionId) {
        return new AuthorisationResponse(false, errorMessage, null, transactionId);
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

    public String getTransactionId() {
        return transactionId;
    }
}
