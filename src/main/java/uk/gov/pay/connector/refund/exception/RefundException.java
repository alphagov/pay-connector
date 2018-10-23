package uk.gov.pay.connector.refund.exception;

import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;
import static uk.gov.pay.connector.util.ResponseUtil.preconditionFailedResponse;

public class RefundException extends WebApplicationException {

    public static RefundException refundAmountAvailableMismatchException(String message){
        return new RefundException(message);
    }

    public static RefundException refundException(String message, ErrorCode code) {
        return new RefundException(message, code);
    }

    private RefundException(String message, ExternalChargeRefundAvailability refundAvailability) {
        super(badRequestResponse(refundAvailability.getStatus(), message));
    }

    private RefundException(String message, ErrorCode errorCode) {
        super(badRequestResponse(errorCode.getValue(), message));
    }

    private RefundException(String message) {
        super(preconditionFailedResponse(message));
    }

    public static RefundException notAvailableForRefundException(String chargeId, ExternalChargeRefundAvailability currentAvailability) {
        return new RefundException(format("Charge with id [%s] not available for refund.", chargeId), currentAvailability);
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
