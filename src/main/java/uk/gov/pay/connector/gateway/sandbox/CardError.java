package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

public class CardError {
    private ChargeStatus newChargeStatus;
    private String errorMessage;

    public CardError(ChargeStatus newChargeStatus, String errorMessage) {
        this.newChargeStatus = newChargeStatus;
        this.errorMessage = errorMessage;
    }

    public ChargeStatus getNewErrorStatus() {
        return newChargeStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
