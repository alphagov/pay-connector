package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.GatewayError.baseGatewayError;
import static uk.gov.pay.connector.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

public class AuthorisationResponse implements GatewayResponse {
    private boolean successful;
    private GatewayError error;
    private ChargeStatus status;
    private String transactionId;

    public AuthorisationResponse(boolean successful, GatewayError error, ChargeStatus status, String transactionId) {
        this.successful = successful;
        this.error = error;
        this.status = status;
        this.transactionId = transactionId;
    }

    public AuthorisationResponse(GatewayError error) {
        this.error = error;
    }

    public static AuthorisationResponse successfulAuthorisation(ChargeStatus status, String transactionId) {
        return new AuthorisationResponse(true, null, status, transactionId);
    }

    public static AuthorisationResponse errorResponse(Logger logger, Response response) {
        logger.error(format("Error code received from gateway: %s.", response.getStatus()));
        return new AuthorisationResponse(baseGatewayError("Error processing request"));
    }

    public static AuthorisationResponse errorCardAuthResponse(Logger logger, Response response) {
        logger.error(format("Error code received from provider: response status = %s.", response.getStatus()));
        return new AuthorisationResponse(unexpectedStatusCodeFromGateway("Error processing capture request"));
    }

    public static AuthorisationResponse authorisationFailureNotUpdateResponse(Logger logger, String transactionId, String errorMessage) {
        logger.error(format("Error received from gateway: %s.", errorMessage));
        return new AuthorisationResponse(false, baseGatewayError(errorMessage), null, transactionId);
    }

    public static AuthorisationResponse authorisationFailureResponse(Logger logger, String transactionId, String errorMessage) {
        logger.error(format("Failed to authorise transaction with id %s: %s", transactionId, errorMessage));
        return new AuthorisationResponse(false, baseGatewayError("This transaction was declined."), AUTHORISATION_REJECTED, transactionId);
    }

    public static AuthorisationResponse authorisationFailureResponse(GatewayError gatewayError) {
        return new AuthorisationResponse(gatewayError);
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
