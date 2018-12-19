package uk.gov.pay.connector.gateway.worldpay;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayRefundHandler implements RefundHandler {

    private final GatewayClient client;

    public WorldpayRefundHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildRefundOrder(request));

        if (response.isLeft()) {
            return GatewayRefundResponse.fromGatewayError(response.left().value());
        } else {
            Either<GatewayError, WorldpayRefundResponse> unmarshalled = client.unmarshallResponse(response.right().value(), WorldpayRefundResponse.class);
            return fromUnmarshalled(unmarshalled, GatewayRefundResponse.RefundState.PENDING);
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getReference())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
