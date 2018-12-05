package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.GatewayError;

import java.util.Optional;

public class CaptureResponse {

    private String transactionId;
    private ChargeState chargeState;
    private GatewayError gatewayError;

    private CaptureResponse(String transactionId, ChargeState chargeState) {
        this.transactionId = transactionId;
        this.chargeState = chargeState;
    }

    public CaptureResponse(GatewayError gatewayError) {
        this.gatewayError = gatewayError;
    }

    public static CaptureResponse fromTransactionId(String transactionId, ChargeState chargeState) {
        return new CaptureResponse(transactionId, chargeState);
    }

    public static CaptureResponse fromGatewayError(GatewayError gatewayError) {
        return new CaptureResponse(gatewayError);
    }

    public Optional<GatewayError> getError() {
        return Optional.ofNullable(gatewayError);
    }

    /**
     * To avoid returning a null, one must call getError first to determine if there is an error
     */
    public ChargeState state() {
        return chargeState;
    }

    public Optional<String> getTransactionId() {
        return Optional.ofNullable(transactionId);
    }

    public boolean isSuccessful() {
        return gatewayError == null;
    }

    public enum ChargeState {
        COMPLETE, PENDING
    }
}
