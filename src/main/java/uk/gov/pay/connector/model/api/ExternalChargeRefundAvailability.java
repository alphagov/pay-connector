package uk.gov.pay.connector.model.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.resources.PaymentProviderValidator.WORLDPAY_PROVIDER;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ExternalChargeRefundAvailability {

    EXTERNAL_AVAILABLE("available"),
    EXTERNAL_UNAVAILABLE("unavailable"),
    EXTERNAL_PENDING("pending"),
    EXTERNAL_FULL("full");

    private final String value;

    ExternalChargeRefundAvailability(String value) {
        this.value = value;
    }

    public String getStatus() {
        return value;
    }

    public String toString() {
        return this.value;
    }

    private static final ChargeStatus[] PENDING_FOR_REFUND_STATUS = new ChargeStatus[]{
            CREATED, ENTERING_CARD_DETAILS, AUTHORISATION_READY, AUTHORISATION_SUCCESS, CAPTURE_READY, CAPTURE_SUBMITTED
    };

    public static ExternalChargeRefundAvailability valueOf(ChargeEntity charge) {

        ExternalChargeRefundAvailability refundAvailabilityStatusResult = EXTERNAL_UNAVAILABLE;

        if (charge.getGatewayAccount().getGatewayName().equals(WORLDPAY_PROVIDER)) {
            if (charge.hasStatus(PENDING_FOR_REFUND_STATUS)) {
                refundAvailabilityStatusResult = EXTERNAL_PENDING;

            } else if (charge.hasStatus(CAPTURED)) {
                if (charge.getTotalAmountToBeRefunded() > 0) {
                    refundAvailabilityStatusResult = EXTERNAL_AVAILABLE;
                } else {
                    refundAvailabilityStatusResult = EXTERNAL_FULL;
                }
            }
        }

        return refundAvailabilityStatusResult;
    }

}
