package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayException.GenericGatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqRefundResponse;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForRefundOrder;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getEpdqAuthHeader;

public class EpdqRefundHandler implements RefundHandler {

    private final GatewayClient client;
    private final Map<String, String> gatewayUrlMap;

    public EpdqRefundHandler(GatewayClient client, Map<String, String> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        try {
            URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(request.getGatewayAccount().getType()), ROUTE_FOR_MAINTENANCE_ORDER));
            GatewayClient.Response response = client.postRequestFor(
                    url,
                    EPDQ,
                    request.getGatewayAccount().getType(),
                    buildRefundOrder(request), 
                    getEpdqAuthHeader(request.getGatewayCredentials()));
            return GatewayRefundResponse.fromBaseRefundResponse(unmarshallResponse(response, EpdqRefundResponse.class), PENDING);
        } catch (GenericGatewayException | GatewayException.GatewayConnectionTimeoutException | GatewayErrorException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        EpdqCredentials credentials = (EpdqCredentials) request.getGatewayCredentials();
        var epdqPayloadDefinitionForRefundOrder = new EpdqPayloadDefinitionForRefundOrder();
        epdqPayloadDefinitionForRefundOrder.setUserId(credentials.getUsername());
        epdqPayloadDefinitionForRefundOrder.setPassword(credentials.getPassword());
        epdqPayloadDefinitionForRefundOrder.setPspId(credentials.getMerchantId());
        epdqPayloadDefinitionForRefundOrder.setPayId(request.getTransactionId());
        epdqPayloadDefinitionForRefundOrder.setAmount(request.getAmount());
        epdqPayloadDefinitionForRefundOrder.setShaInPassphrase(credentials.getShaInPassphrase());
        return epdqPayloadDefinitionForRefundOrder.createGatewayOrder();
    }
}
