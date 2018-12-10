package uk.gov.pay.connector.gateway.epdq;

import fj.data.Either;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.epdq.model.response.EpdqCaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import static uk.gov.pay.connector.gateway.epdq.EpdqOrderRequestBuilder.anEpdqCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gateway.epdq.EpdqPaymentProvider.ROUTE_FOR_MAINTENANCE_ORDER;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_PASSWORD;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_SHA_IN_PASSPHRASE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_USERNAME;

public class EpdqCaptureHandler implements CaptureHandler {

    private final GatewayClient client;

    public EpdqCaptureHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(ROUTE_FOR_MAINTENANCE_ORDER, request.getGatewayAccount(), buildCaptureOrder(request));

        if (response.isLeft()) {
            return CaptureResponse.fromGatewayError(response.left().value());
        } else {
            Either<GatewayError, EpdqCaptureResponse> unmarshalled = client.unmarshallResponse(response.right().value(), EpdqCaptureResponse.class);
            return fromUnmarshalled(unmarshalled, CaptureResponse.ChargeState.PENDING);
        }
    }

    private GatewayOrder buildCaptureOrder(CaptureGatewayRequest request) {
        return anEpdqCaptureOrderRequestBuilder()
                .withUserId(request.getGatewayAccount().getCredentials().get(CREDENTIALS_USERNAME))
                .withPassword(request.getGatewayAccount().getCredentials().get(CREDENTIALS_PASSWORD))
                .withShaInPassphrase(request.getGatewayAccount().getCredentials().get(
                        CREDENTIALS_SHA_IN_PASSPHRASE))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
