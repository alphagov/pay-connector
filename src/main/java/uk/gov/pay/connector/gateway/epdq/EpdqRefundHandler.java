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

import java.net.URI;
import java.util.Map;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.EPDQ;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getGatewayAccountCredentialsAsAuthHeader;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

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
                    getGatewayAccountCredentialsAsAuthHeader(request.getGatewayCredentials()));
            return GatewayRefundResponse.fromBaseRefundResponse(unmarshallResponse(response, EpdqRefundResponse.class), PENDING);
        } catch (GenericGatewayException | GatewayException.GatewayConnectionTimeoutException | GatewayErrorException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        var epdqPayloadDefinitionForRefundOrder = new EpdqPayloadDefinitionForRefundOrder();
        epdqPayloadDefinitionForRefundOrder.setUserId(request.getGatewayCredentials().get(CREDENTIALS_USERNAME));
        epdqPayloadDefinitionForRefundOrder.setPassword(request.getGatewayCredentials().get(CREDENTIALS_PASSWORD));
        epdqPayloadDefinitionForRefundOrder.setPspId(request.getGatewayCredentials().get(CREDENTIALS_MERCHANT_ID));
        epdqPayloadDefinitionForRefundOrder.setPayId(request.getTransactionId());
        epdqPayloadDefinitionForRefundOrder.setAmount(request.getAmount());
        epdqPayloadDefinitionForRefundOrder.setShaInPassphrase(request.getGatewayCredentials().get(CREDENTIALS_SHA_IN_PASSPHRASE));
        return epdqPayloadDefinitionForRefundOrder.createGatewayOrder();
    }
}
