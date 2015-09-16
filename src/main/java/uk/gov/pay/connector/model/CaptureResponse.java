package uk.gov.pay.connector.model;

public class CaptureResponse {
    private boolean successful = true;

    private String errorMessage;

    public CaptureResponse(boolean successful, String errorMessage) {
        this.successful = successful;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static CaptureResponse aSuccessfulResponse(){
        return new CaptureResponse(true, null);
    }
}
