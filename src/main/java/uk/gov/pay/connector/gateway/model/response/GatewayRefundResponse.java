package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.gateway.model.GatewayError;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;

public class GatewayRefundResponse {

    private final Optional<String> reference;
    private final GatewayRefundResponse.RefundState refundState;
    private final GatewayError gatewayError;
    private final String stringified;

    private GatewayRefundResponse(Optional<String> reference, RefundState refundState,
                                  GatewayError gatewayError, String stringified) {
        this.reference = reference;
        this.refundState = refundState;
        this.gatewayError = gatewayError;
        this.stringified = stringified;
    }

    public static GatewayRefundResponse fromGatewayError(GatewayError gatewayError) {
        return new GatewayRefundResponse(Optional.empty(), RefundState.ERROR, gatewayError, gatewayError.toString());
    }

    public static GatewayRefundResponse fromBaseRefundResponse(BaseRefundResponse refundResponse, RefundState refundState) {
        if (isNotBlank(refundResponse.getErrorCode()) || isNotBlank(refundResponse.getErrorMessage())) {
            return new GatewayRefundResponse(refundResponse.getReference(), refundState, genericGatewayError(refundResponse.stringify()), refundResponse.stringify());
        } else
            return new GatewayRefundResponse(refundResponse.getReference(), refundState, null, refundResponse.stringify());
    }

    public Optional<GatewayError> getError() {
        return Optional.ofNullable(gatewayError);
    }

    public GatewayRefundResponse.RefundState state() {
        return refundState;
    }

    public Optional<String> getReference() {
        return reference;
    }

    public boolean isSuccessful() {
        return gatewayError == null;
    }

    public enum RefundState {
        COMPLETE, PENDING, ERROR
    }

    @Override
    public String toString() {
        return stringified;
    }
}
