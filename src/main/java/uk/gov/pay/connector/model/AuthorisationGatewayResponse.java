package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;

public class AuthorisationGatewayResponse extends GatewayResponse {

    static private final Logger logger = LoggerFactory.getLogger(AuthorisationGatewayResponse.class);

    private ChargeStatus status;
    private String transactionId;

    public AuthorisationGatewayResponse(ResponseStatus responseStatus, ErrorResponse error, ChargeStatus status, String transactionId) {
        this.responseStatus = responseStatus;
        this.error = error;
        this.status = status;
        this.transactionId = transactionId;
    }

    private AuthorisationGatewayResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = AUTHORISATION_ERROR;
    }

    public static AuthorisationGatewayResponse successfulAuthorisationResponse(ChargeStatus status, String transactionId) {
        return new AuthorisationGatewayResponse(SUCCEDED, null, status, transactionId);
    }

    public static AuthorisationGatewayResponse authorisationFailureNotUpdateResponse(String transactionId, String errorMessage) {
        logger.error(format("Error received from gateway: %s.", errorMessage));
        return new AuthorisationGatewayResponse(FAILED, baseError(errorMessage), AUTHORISATION_ERROR, transactionId);
    }

    public static AuthorisationGatewayResponse authorisationFailureResponse(String transactionId, String errorMessage) {
        logger.error(format("Failed to authorise transaction with id %s: %s", transactionId, errorMessage));
        return new AuthorisationGatewayResponse(FAILED, baseError("This transaction was declined."), AUTHORISATION_REJECTED, transactionId);
    }

    public static AuthorisationGatewayResponse authorisationFailureResponse(ErrorResponse errorResponse) {
        return new AuthorisationGatewayResponse(errorResponse);
    }

    public ChargeStatus getNewChargeStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }
}
