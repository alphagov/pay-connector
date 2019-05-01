package uk.gov.pay.connector.refund.exception;

import uk.gov.pay.connector.common.model.api.ErrorIdentifier;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import static java.lang.String.format;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static uk.gov.pay.connector.common.model.api.ErrorIdentifier.REFUND_AMOUNT_AVAILABLE_MISMATCH;
import static uk.gov.pay.connector.common.model.api.ErrorIdentifier.REFUND_NOT_AVAILABLE;

public class RefundException extends WebApplicationException {

    public static RefundException refundAmountAvailableMismatchException(String message) {
        return new RefundException(message, REFUND_AMOUNT_AVAILABLE_MISMATCH, PRECONDITION_FAILED, null);
    }

    public static RefundException notAvailableForRefundException(String message, ErrorCode code) {
        return new RefundException(message, REFUND_NOT_AVAILABLE, BAD_REQUEST, code.getValue());
    }

    public static RefundException notAvailableForRefundException(String chargeId, ExternalChargeRefundAvailability currentAvailability) {
        return new RefundException(format("Charge with id [%s] not available for refund.", chargeId),
                REFUND_NOT_AVAILABLE, BAD_REQUEST, currentAvailability.getStatus());
    }

    private RefundException(String message, ErrorIdentifier errorIdentifier, Response.Status status, String reason) {
        super(Response.status(status)
                .entity(new ErrorResponse(errorIdentifier, message, reason))
                .build());
    }

    public enum ErrorCode {

        NOT_SUFFICIENT_AMOUNT_AVAILABLE("amount_not_available"),
        MINIMUM_AMOUNT("amount_min_validation");

        private String value;

        ErrorCode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
