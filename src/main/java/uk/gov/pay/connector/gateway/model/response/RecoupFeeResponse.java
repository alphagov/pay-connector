package uk.gov.pay.connector.gateway.model.response;

public class RecoupFeeResponse {
    private final boolean successful;
    private final Long amountCollected;
    private final String errorMessage;

    public static RecoupFeeResponse fromException(Exception e) {
        return new RecoupFeeResponse(false, null, e.getMessage());
    }

    public RecoupFeeResponse(boolean successful, Long amountCollected, String errorMessage) {
        this.successful = successful;
        this.amountCollected = amountCollected;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Long getAmountCollected() {
        return amountCollected;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
