package uk.gov.pay.connector.gateway.smartpay;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayRefundHandler implements RefundHandler {

    private final GatewayClient client;

    public SmartpayRefundHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildRefundOrderFor(request));

        if (response.isLeft()) {
            return GatewayRefundResponse.fromGatewayError(response.left().value());
        } else {
            Either<GatewayError, SmartpayRefundResponse> unmarshalled = client.unmarshallResponse(response.right().value(), SmartpayRefundResponse.class);
            return fromUnmarshalled(unmarshalled, GatewayRefundResponse.RefundState.PENDING);
        }
    }

    private GatewayOrder buildRefundOrderFor(RefundGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .withAmount(request.getAmount())
                .build();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }
}
