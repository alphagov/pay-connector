package uk.gov.pay.connector.model;

public class CardAuthorisationResponse {

    private Boolean successful;
    private final String errorMessage;
    private final ChargeStatus status;

    public CardAuthorisationResponse(Boolean successful, String errorMessage, ChargeStatus status) {
        this.successful = successful;
        this.errorMessage = errorMessage;
        this.status = status;
    }

    public static CardAuthorisationResponse successfulAuthorisation(ChargeStatus status) {
        return new CardAuthorisationResponse(true, "", status);
    }

    public static CardAuthorisationResponse anErrorResponse(String errorMessage) {
        return new CardAuthorisationResponse(false, errorMessage, null);
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ChargeStatus getNewChargeStatus() {
        return status;
    }
}
