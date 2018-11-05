package uk.gov.pay.connector.paymentprocessor.model;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;

public enum OperationType {
    
    CAPTURE("Capture", CAPTURE_READY),
    AUTHORISATION("Authorisation", AUTHORISATION_READY),
    AUTHORISATION_3DS("3D Secure Response Authorisation", AUTHORISATION_3DS_READY),
    CANCELLATION("Cancellation", null);

    private String value;
    private ChargeStatus lockingStatus;

    OperationType(String value, ChargeStatus lockingStatus) {
        this.value = value;
        this.lockingStatus = lockingStatus;
    }

    public String getValue() {
        return value;
    }

    public ChargeStatus getLockingStatus() {
        return lockingStatus;
    }

}
