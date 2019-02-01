package uk.gov.pay.connector.gateway.smartpay;

import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.request.GatewayRequest;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;

import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayRefundHandler implements RefundHandler {

    private final GatewayClient client;

    public SmartpayRefundHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        SmartpayRefundResponse unmarshalled;
        
        try {
            GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), buildRefundOrderFor(request));
            unmarshalled = unmarshallResponse(response, SmartpayRefundResponse.class);
        } catch (GenericGatewayErrorException | GatewayConnectionTimeoutErrorException | GatewayConnectionErrorException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
        
        return GatewayRefundResponse.fromBaseRefundResponse(unmarshalled, PENDING);
    }

    private GatewayOrder buildRefundOrderFor(RefundGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayRefundOrderRequestBuilder()
                .withReference(request.getRefundExternalId())
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(getMerchantCode(request))
                .withAmount(request.getAmount())
                .build();
    }

    private String getMerchantCode(GatewayRequest request) {
        return request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID);
    }
}
