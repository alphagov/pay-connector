package uk.gov.pay.connector.gateway.smartpay;

import fj.data.Either;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.util.GatewayResponseGenerator;

import javax.ws.rs.client.Invocation;
import java.util.function.BiFunction;

import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class SmartpayCaptureHandler implements CaptureHandler {

    private final GatewayClient client;

    public SmartpayCaptureHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public GatewayResponse<SmartpayCaptureResponse> capture(CaptureGatewayRequest request) {
        Either<GatewayError, GatewayClient.Response> response = client.postRequestFor(null, request.getGatewayAccount(), buildCaptureOrderFor(request));
        return GatewayResponseGenerator.getSmartpayGatewayResponse(client, response, SmartpayCaptureResponse.class);
    }

    public static BiFunction<GatewayOrder, Invocation.Builder, Invocation.Builder> includeSessionIdentifier() {
        return (order, builder) -> builder;
    }

    private GatewayOrder buildCaptureOrderFor(CaptureGatewayRequest request) {
        return SmartpayOrderRequestBuilder.aSmartpayCaptureOrderRequestBuilder()
                .withTransactionId(request.getTransactionId())
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .build();
    }
}
