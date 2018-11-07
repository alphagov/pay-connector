package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;

import static java.util.UUID.randomUUID;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

public class SandboxCaptureHandler implements CaptureHandler {

    @Override
    public GatewayResponse<BaseCaptureResponse> capture(CaptureGatewayRequest request) {
        GatewayResponse.GatewayResponseBuilder<BaseCaptureResponse> gatewayResponseBuilder = responseBuilder();
        return gatewayResponseBuilder.withResponse(new BaseCaptureResponse() {

            private final String transactionId = randomUUID().toString();

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }

            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public String toString() {
                return "Sandbox capture response (transactionId: " + getTransactionId() + ')';
            }
        }).build();

    }
}
