package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.gateway.PaymentGatewayName;

import static java.lang.String.format;

public interface BaseCaptureResponse extends BaseResponse {

    String getTransactionId();

    String stringify();

    static BaseCaptureResponse fromTransactionId(String transactionId, PaymentGatewayName gatewayName) {
        return new BaseCaptureResponse() {
            @Override
            public String getTransactionId() {
                return transactionId;
            }

            @Override
            public String stringify() {
                return format("%s capture response: (transactionId: %s)", gatewayName.getName(), transactionId);
            }

            @Override
            public String getErrorCode() {
                return null;
            }

            @Override
            public String getErrorMessage() {
                return null;
            }
        };
    }
}
