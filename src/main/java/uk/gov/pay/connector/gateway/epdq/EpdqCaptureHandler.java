package uk.gov.pay.connector.gateway.epdq;

import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.epdq.payload.EpdqPayloadDefinitionForCaptureOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

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
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayCredentials()));
            return CaptureResponse.fromBaseCaptureResponse(unmarshallResponse(response, EpdqCaptureResponse.class), PENDING);
        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildCaptureOrder(CaptureGatewayRequest request) {
        var epdqPayloadDefinitionForCaptureOrder = new EpdqPayloadDefinitionForCaptureOrder();
        epdqPayloadDefinitionForCaptureOrder.setUserId(request.getGatewayCredentials().get(CREDENTIALS_USERNAME).toString());
        epdqPayloadDefinitionForCaptureOrder.setPassword(request.getGatewayCredentials().get(CREDENTIALS_PASSWORD).toString());
        epdqPayloadDefinitionForCaptureOrder.setPspId(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID).toString());
        epdqPayloadDefinitionForCaptureOrder.setPayId(request.getGatewayTransactionId());
        epdqPayloadDefinitionForCaptureOrder.setShaInPassphrase(request.getGatewayCredentials().get(CREDENTIALS_SHA_IN_PASSPHRASE).toString());
        return epdqPayloadDefinitionForCaptureOrder.createGatewayOrder();
    }
}
