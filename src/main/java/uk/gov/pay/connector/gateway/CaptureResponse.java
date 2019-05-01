package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public class CaptureResponse {

    private final String transactionId;
    private final ChargeState chargeState;
    private final GatewayError gatewayError;
    private final String stringified;
    private Long feeAmount;

    private CaptureResponse(String transactionId, ChargeState chargeState, GatewayError gatewayError, String stringified) {
        this.transactionId = transactionId;
        this.chargeState = chargeState;
        this.gatewayError = gatewayError;
        this.stringified = stringified;
    }

    private CaptureResponse(String transactionId, ChargeState chargeState, GatewayError gatewayError, String stringified, Long feeAmount) {
        this.transactionId = transactionId;
        this.chargeState = chargeState;
        this.gatewayError = gatewayError;
        this.stringified = stringified;
        this.feeAmount = feeAmount;
    }

    public CaptureResponse(String transactionId, ChargeState chargeState, Long feeAmount) {
        this(transactionId, chargeState, null, null, feeAmount);
    }

    public CaptureResponse(GatewayError gatewayError, String stringified) {
        this(null, null, gatewayError, stringified, null);
    }

    public static CaptureResponse fromGatewayError(GatewayError gatewayError) {
        return new CaptureResponse(null, null, gatewayError, gatewayError.toString());
    }

    public static CaptureResponse fromBaseCaptureResponse(BaseCaptureResponse captureResponse, ChargeState chargeState) {
        if (isNotBlank(captureResponse.getErrorCode())) {
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState, genericGatewayError(captureResponse.stringify()), captureResponse.stringify());
        } else
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState,null, captureResponse.stringify());
    }

    public Optional<GatewayError> getError() {
        return Optional.ofNullable(gatewayError);
    }

    /**
     * To avoid returning a null, one must call isSuccessful first to determine if there is an error
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

    public Optional<Long> getFee() {
        return Optional.ofNullable(feeAmount);
    }

    public enum ChargeState {
        COMPLETE, PENDING
    }

    @Override
    public String toString() {
        return stringified;
    }
}
