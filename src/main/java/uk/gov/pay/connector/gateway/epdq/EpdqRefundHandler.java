package uk.gov.pay.connector.gateway.epdq;

import fj.data.Either;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqRefundResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqRefundOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class EpdqRefundHandler implements RefundHandler {

    private final GatewayClient client;

    public EpdqRefundHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(ROUTE_FOR_MAINTENANCE_ORDER, request.getGatewayAccount(), buildRefundOrder(request));

        if (response.isLeft()) {
            return GatewayRefundResponse.fromGatewayError(response.left().value());
        } else {
            Either<GatewayError, EpdqRefundResponse> unmarshalled = client.unmarshallResponse(response.right().value(), EpdqRefundResponse.class);
            return fromUnmarshalled(unmarshalled, GatewayRefundResponse.RefundState.PENDING);
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return anEpdqRefundOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .withAmount(request.getAmount())
                .build();
    }
}
