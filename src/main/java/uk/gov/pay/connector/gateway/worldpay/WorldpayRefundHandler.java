package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;

import javax.inject.Inject;
import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;

public class WorldpayRefundHandler implements RefundHandler {

    private final GatewayClient client;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayRefundHandler(@Named("WorldpayRefundGatewayClient") GatewayClient client,
                                 @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        try {
            GatewayClient.Response response = client.postRequestFor(
                    gatewayUrlMap.get(request.gatewayAccount().getType()),
                    WORLDPAY,
                    request.gatewayAccount().getType(),
                    buildRefundOrder(request), 
                    getWorldpayAuthHeader(request.getGatewayCredentials(), request.authorisationMode(), request.isForRecurringPayment()));
            return GatewayRefundResponse.fromBaseRefundResponse(unmarshallResponse(response, WorldpayRefundResponse.class), PENDING);
        } catch (GatewayException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return aWorldpayRefundOrderRequestBuilder()
                .withReference(request.refundExternalId())
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.authorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.amount())
                .withTransactionId(request.transactionId())
                .build();
    }
}
