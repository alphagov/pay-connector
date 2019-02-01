package uk.gov.pay.connector.gateway.worldpay;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccount.CREDENTIALS_MERCHANT_ID;

public class WorldpayCaptureHandler implements CaptureHandler {

    private final GatewayClient client;

    public WorldpayCaptureHandler(GatewayClient client) {
        this.client = client;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        WorldpayCaptureResponse unmarshalled;
        
        try {
            GatewayClient.Response response = client.postRequestFor(null, request.getGatewayAccount(), buildCaptureOrder(request));
            unmarshalled = unmarshallResponse(response, WorldpayCaptureResponse.class);
        } catch (GenericGatewayErrorException | GatewayConnectionTimeoutErrorException | GatewayConnectionErrorException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
        
        return CaptureResponse.fromBaseCaptureResponse(unmarshalled, PENDING);
     }

    private GatewayOrder buildCaptureOrder(CaptureGatewayRequest request) {
        return aWorldpayCaptureOrderRequestBuilder()
                .withDate(DateTime.now(DateTimeZone.UTC))
                .withMerchantCode(request.getGatewayAccount().getCredentials().get(CREDENTIALS_MERCHANT_ID))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
