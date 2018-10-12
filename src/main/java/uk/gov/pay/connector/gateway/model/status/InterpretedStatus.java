package uk.gov.pay.connector.gateway.model.status;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;

public interface InterpretedStatus {

    enum Type {
        CHARGE_STATUS,
        REFUND_STATUS,
        UNKNOWN,
        IGNORED
    }

    Type getType();

    default ChargeStatus getChargeStatus() {
        throw new IllegalStateException("This object does not have a ChargeStatus");
    }

    default RefundStatus getRefundStatus() {
        throw new IllegalStateException("This object does not have a RefundStatus");
    }

}




