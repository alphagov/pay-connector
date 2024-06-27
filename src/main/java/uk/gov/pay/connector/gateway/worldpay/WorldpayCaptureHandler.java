package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.util.AuthUtil;

import javax.inject.Inject;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.PENDING;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayCaptureOrderRequestBuilder;

public class WorldpayCaptureHandler implements CaptureHandler {

    private final GatewayClient client;
    private final Map<String, URI> gatewayUrlMap;

    @Inject
    public WorldpayCaptureHandler(@Named("WorldpayCaptureGatewayClient") GatewayClient client,
                                  @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {
        try {
            GatewayClient.Response response = client.postRequestFor(
                    gatewayUrlMap.get(request.gatewayAccount().getType()),
                    WORLDPAY,
                    request.gatewayAccount().getType(),
                    buildCaptureOrder(request),
                    getWorldpayAuthHeader(request.gatewayCredentials(), request.authorisationMode(), request.isForRecurringPayment()));
            return CaptureResponse.fromBaseCaptureResponse(unmarshallResponse(response, WorldpayCaptureResponse.class), PENDING);
        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
     }

    private GatewayOrder buildCaptureOrder(CaptureGatewayRequest request) {
        return aWorldpayCaptureOrderRequestBuilder()
                .withDate(LocalDate.now(ZoneOffset.UTC))
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.gatewayCredentials(), request.authorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.getAmountAsString())
                .withTransactionId(request.gatewayTransactionId())
                .build();
    }

}
