package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayRefundHandler implements RefundHandler {

    private final GatewayClient client;
    private final Map<String, URI> gatewayUrlMap;

    public WorldpayRefundHandler(GatewayClient client, Map<String, URI> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        try {
            GatewayClient.Response response = client.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()), 
                    request.getGatewayAccount(), 
                    buildRefundOrder(request), 
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayAccount()));
            return GatewayRefundResponse.fromBaseRefundResponse(unmarshallResponse(response, WorldpayRefundResponse.class), PENDING);
        } catch (GatewayException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getRefundExternalId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
