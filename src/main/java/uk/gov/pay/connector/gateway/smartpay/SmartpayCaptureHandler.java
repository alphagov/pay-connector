package uk.gov.pay.connector.gateway.smartpay;

import fj.data.Either;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import static java.lang.String.format;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayCaptureHandler implements CaptureHandler {

    private final GatewayClient client;

    public SmartpayCaptureHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildCaptureOrderFor(request));

        if (response.isLeft()) {
            return CaptureResponse.fromGatewayError(response.left().value());
        } else {
            Either<GatewayError, SmartpayCaptureResponse> unmarshalled = client.unmarshallResponse(response.right().value(), SmartpayCaptureResponse.class);
            return fromUnmarshalled(unmarshalled, CaptureResponse.ChargeState.PENDING);
        }
    }

    private GatewayOrder buildCaptureOrderFor(CaptureGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .build();
    }
}
