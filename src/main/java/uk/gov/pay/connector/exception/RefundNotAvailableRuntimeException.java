package uk.gov.pay.connector.exception;

import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;

import javax.ws.rs.WebApplicationException;

import static java.lang.String.format;
import static uk.gov.pay.connector.util.ResponseUtil.badRequestResponse;

public class RefundNotAvailableRuntimeException extends WebApplicationException {

    public RefundNotAvailableRuntimeException(String chargeId, ExternalChargeRefundAvailability refundAvailability) {
        super(badRequestResponse(refundAvailability.getStatus(), format("Charge with id [%s] not available for refund.", chargeId)));
    }

    public RefundNotAvailableRuntimeException(String chargeId, ErrorCode errorCode) {
        super(badRequestResponse(errorCode.getValue(), format("Charge with id [%s] not available for refund.", chargeId)));
    }

    public enum ErrorCode {

        NOT_SUFFICIENT_AMOUNT_AVAILABLE("amount_not_available");

        private String value;

        ErrorCode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
