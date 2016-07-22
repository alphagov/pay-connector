package uk.gov.pay.connector.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.RefundStatus;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.ErrorResponse.baseError;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.FAILED;
import static uk.gov.pay.connector.model.GatewayResponse.ResponseStatus.SUCCEDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class RefundGatewayResponse extends GatewayResponse {

    static private final Logger logger = LoggerFactory.getLogger(RefundGatewayResponse.class);

    private final RefundStatus status;

    public RefundGatewayResponse(ResponseStatus responseStatus, ErrorResponse errorMessage, RefundStatus status) {
        this.responseStatus = responseStatus;
        this.error = errorMessage;
        this.status = status;
    }

    public RefundGatewayResponse(ErrorResponse error) {
        this.responseStatus = FAILED;
        this.error = error;
        this.status = REFUND_ERROR;
    }

    public static RefundGatewayResponse successfulResponse(RefundStatus status) {
        return new RefundGatewayResponse(SUCCEDED, null, status);
    }

    public static RefundGatewayResponse failureResponse(String errorMessage) {
        logger.error(format("Failed to refund charge: %s", errorMessage));
        return new RefundGatewayResponse(FAILED, baseError(errorMessage), REFUND_ERROR);
    }

    public static RefundGatewayResponse failureResponse(ErrorResponse errorResponse) {
        return new RefundGatewayResponse(errorResponse);
    }

    public RefundStatus getStatus() {
        return status;
    }
}
