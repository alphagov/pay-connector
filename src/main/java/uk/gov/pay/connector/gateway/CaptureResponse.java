package uk.gov.pay.connector.gateway;

import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public class CaptureResponse {

    private final String transactionId;
    private final ChargeState chargeState;
    private final GatewayError gatewayError;
    private final String stringified;
    private final List<Fee> feeList;

    private CaptureResponse(String transactionId, ChargeState chargeState, GatewayError gatewayError,
                            String stringified, List<Fee> feeList) {
        this.transactionId = transactionId;
        this.chargeState = chargeState;
        this.gatewayError = gatewayError;
        this.stringified = stringified;
        this.feeList = feeList;
    }

    private CaptureResponse(String transactionId, ChargeState chargeState, GatewayError gatewayError, String stringified) {
        this(transactionId, chargeState, gatewayError, stringified, Collections.emptyList());
    }

    public CaptureResponse(String transactionId, ChargeState chargeState, List<Fee> feeList) {
        this(transactionId, chargeState, null, null, feeList);
    }

    public static CaptureResponse fromGatewayError(GatewayError gatewayError) {
        return new CaptureResponse(null, null, gatewayError, gatewayError.toString());
    }

    public static CaptureResponse fromBaseCaptureResponse(BaseCaptureResponse captureResponse, ChargeState chargeState) {
        if (isNotBlank(captureResponse.getErrorCode())) {
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState, genericGatewayError(captureResponse.stringify()), captureResponse.stringify());
        } else
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState, null, captureResponse.stringify());
    }

    public static CaptureResponse fromBaseCaptureResponse(BaseCaptureResponse captureResponse, ChargeState chargeState, List<Fee> feeList) {
        if (isNotBlank(captureResponse.getErrorCode())) {
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState, genericGatewayError(captureResponse.stringify()), captureResponse.stringify());
        } else
            return new CaptureResponse(captureResponse.getTransactionId(), chargeState, null, captureResponse.stringify(), feeList);
    }

    public Optional<GatewayError> getError() {
        return Optional.ofNullable(gatewayError);
    }

    public Optional<String> getErrorMessage() {
        return getError().map(GatewayError::getMessage);
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

    public List<Fee> getFeeList() {
        return feeList;
    }

    public enum ChargeState {
        COMPLETE, PENDING
    }

    @Override
    public String toString() {
        return stringified;
    }
}
