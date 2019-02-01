package uk.gov.pay.connector.gateway.smartpay;

import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GatewayConnectionTimeoutErrorException;
import uk.gov.pay.connector.gateway.GatewayErrors.GenericGatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayCaptureHandler implements CaptureHandler {

    private final GatewayClient client;

    public SmartpayCaptureHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        SmartpayCaptureResponse unmarshalled;
        
        try {
            GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), buildCaptureOrderFor(request));
            unmarshalled = unmarshallResponse(response, SmartpayCaptureResponse.class);
        } catch (GenericGatewayErrorException | GatewayConnectionTimeoutErrorException | GatewayConnectionErrorException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }

        return CaptureResponse.fromBaseCaptureResponse(unmarshalled, PENDING);
    }

    private GatewayOrder buildCaptureOrderFor(CaptureGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .build();
    }
}
