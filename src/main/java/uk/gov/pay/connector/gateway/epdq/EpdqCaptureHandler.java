package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.EpdqCredentials;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getEpdqAuthHeader;

public class EpdqCaptureHandler implements CaptureHandler {

    private final GatewayClient client;
    private final Map<String, String> gatewayUrlMap;

    public EpdqCaptureHandler(GatewayClient client, Map<String, String> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        try {
            URI url = URI.create(String.format("%s/%s", gatewayUrlMap.get(request.getGatewayAccount().getType()), ROUTE_FOR_MAINTENANCE_ORDER));
            GatewayClient.Response response = client.postRequestFor(
                    url, 
                    EPDQ,
                    request.getGatewayAccount().getType(), 
                    buildCaptureOrder(request),
                    getEpdqAuthHeader(request.getGatewayCredentials()));
            return CaptureResponse.fromBaseCaptureResponse(unmarshallResponse(response, EpdqCaptureResponse.class), PENDING);
        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildCaptureOrder(CaptureGatewayRequest request) {
        EpdqCredentials credentials = (EpdqCredentials) request.getGatewayCredentials();
        var epdqPayloadDefinitionForCaptureOrder = new EpdqPayloadDefinitionForCaptureOrder();
        epdqPayloadDefinitionForCaptureOrder.setUserId(credentials.getUsername());
        epdqPayloadDefinitionForCaptureOrder.setPassword(credentials.getPassword());
        epdqPayloadDefinitionForCaptureOrder.setPspId(credentials.getMerchantId());
        epdqPayloadDefinitionForCaptureOrder.setPayId(request.getGatewayTransactionId());
        epdqPayloadDefinitionForCaptureOrder.setShaInPassphrase(credentials.getShaInPassphrase());
        return epdqPayloadDefinitionForCaptureOrder.createGatewayOrder();
    }
}
