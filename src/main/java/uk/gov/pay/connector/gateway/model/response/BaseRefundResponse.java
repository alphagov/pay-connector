package uk.gov.pay.connector.gateway.model.response;

import uk.gov.pay.connector.gateway.PaymentGatewayName;

import java.util.Optional;

import static java.lang.String.format;

public interface BaseRefundResponse extends BaseResponse {

    Optional<String> getReference();

    String stringify();

    static BaseRefundResponse fromReference(String reference, PaymentGatewayName gatewayName) {
        return new BaseRefundResponse() {
            @Override
            public Optional<String> getReference() {
                return Optional.ofNullable(reference);
            }

            @Override
            public String stringify() {
                return format("%s refund response: (reference: %s)", gatewayName.getName(), reference);
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
