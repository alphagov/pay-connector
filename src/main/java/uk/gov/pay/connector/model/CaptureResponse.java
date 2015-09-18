package uk.gov.pay.connector.model;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class CaptureResponse implements GatewayResponse {

    private final Boolean successful;
    private final GatewayError error;


    public CaptureResponse(boolean successful, GatewayError errorMessage) {
        this.successful = successful;
        this.error = errorMessage;
    }

    public Boolean isSuccessful() {
        return successful;
    }

    public GatewayError getError() {
        return error;
    }

    public static CaptureResponse aSuccessfulCaptureResponse() {
        return new CaptureResponse(true, null);
    }

    public ChargeStatus getNewChargeStatus() {
        return successful ? CAPTURED : null;
    }
}
