package uk.gov.pay.connector.gateway;

import java.util.EnumMap;

public class GatewayOperationClientBuilder {

    EnumMap<GatewayOperation, GatewayClient> operationGatewayClientEnumMap;

    private GatewayOperationClientBuilder() {
        operationGatewayClientEnumMap = new EnumMap<>(GatewayOperation.class);
    }

    public static GatewayOperationClientBuilder builder() {
        return new GatewayOperationClientBuilder();
    }

    public GatewayOperationClientBuilder authClient(GatewayClient gatewayClient) {
        operationGatewayClientEnumMap.put(GatewayOperation.AUTHORISE, gatewayClient);
        return this;
    }

    public GatewayOperationClientBuilder captureClient(GatewayClient gatewayClient) {
        operationGatewayClientEnumMap.put(GatewayOperation.CAPTURE, gatewayClient);
        return this;
    }

    public GatewayOperationClientBuilder cancelClient(GatewayClient gatewayClient) {
        operationGatewayClientEnumMap.put(GatewayOperation.CANCEL, gatewayClient);
        return this;
    }

    public GatewayOperationClientBuilder refundClient(GatewayClient gatewayClient) {
        operationGatewayClientEnumMap.put(GatewayOperation.REFUND, gatewayClient);
        return this;
    }

    public EnumMap<GatewayOperation, GatewayClient> build() {
        return operationGatewayClientEnumMap;
    }
}
