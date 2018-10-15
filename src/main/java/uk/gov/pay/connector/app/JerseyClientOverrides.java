package uk.gov.pay.connector.app;

import uk.gov.pay.connector.gateway.GatewayOperation;

import java.util.EnumMap;
import java.util.Map;

public class JerseyClientOverrides {
    private Map<GatewayOperation, OperationOverrides> operationOverrides = new EnumMap<>(GatewayOperation.class);

    public OperationOverrides getOverridesFor(GatewayOperation operation) {
        return operationOverrides.get(operation);
    }

    public OperationOverrides getAuth() {
        return getOverridesFor(GatewayOperation.AUTHORISE);
    }

    public void setAuth(OperationOverrides auth) {
        operationOverrides.put(GatewayOperation.AUTHORISE, auth);
    }

    public OperationOverrides getCancel() {
        return getOverridesFor(GatewayOperation.CANCEL);
    }

    public void setCancel(OperationOverrides cancel) {
        operationOverrides.put(GatewayOperation.CANCEL, cancel);
    }

    public OperationOverrides getRefund() {
        return getOverridesFor(GatewayOperation.REFUND);
    }

    public void setRefund(OperationOverrides refund) {
        operationOverrides.put(GatewayOperation.REFUND, refund);
    }

    public OperationOverrides getCapture() {
        return getOverridesFor(GatewayOperation.CAPTURE);
    }

    public void setCapture(OperationOverrides capture) {
        operationOverrides.put(GatewayOperation.CAPTURE, capture);
    }
}
