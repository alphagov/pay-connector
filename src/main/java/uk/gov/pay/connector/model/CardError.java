package uk.gov.pay.connector.model;

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
