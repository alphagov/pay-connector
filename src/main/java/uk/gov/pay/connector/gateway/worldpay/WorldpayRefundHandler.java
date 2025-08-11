package uk.gov.pay.connector.gateway.worldpay;

import com.google.inject.name.Named;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayConnectionTimeoutException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.RefundHandler;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;

import jakarta.inject.Inject;
import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.pay.connector.gateway.GatewayResponseUnmarshaller.unmarshallResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.RefundState.PENDING;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getWorldpayAuthHeader;
import static uk.gov.pay.connector.gateway.worldpay.WorldpayOrderRequestBuilder.aWorldpayRefundOrderRequestBuilder;
import static uk.gov.service.payments.logging.LoggingKeys.REFUND_EXTERNAL_ID;

public class WorldpayRefundHandler implements RefundHandler {

    private final GatewayClient client;
    private final Map<String, URI> gatewayUrlMap;
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Inject
    public WorldpayRefundHandler(@Named("WorldpayRefundGatewayClient") GatewayClient client,
                                 @Named("WorldpayGatewayUrlMap") Map<String, URI> gatewayUrlMap) {
        this.client = client;
        this.gatewayUrlMap = gatewayUrlMap;
    }

    @Override
    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        try {
            GatewayClient.Response response = client.postRequestFor(
                    gatewayUrlMap.get(request.getGatewayAccount().getType()),
                    WORLDPAY,
                    request.getGatewayAccount().getType(),
                    buildRefundOrder(request), 
                    getWorldpayAuthHeader(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()));
            return GatewayRefundResponse.fromBaseRefundResponse(unmarshallResponse(response, WorldpayRefundResponse.class), PENDING);
        } catch (GatewayConnectionTimeoutException e) {
            logger.info("Despite a Worldpay Gateway connection timeout error, we are optimistically setting refund {} as SUBMITTED.",
                    request.getRefundExternalId(), kv(REFUND_EXTERNAL_ID, request.getRefundExternalId()));
            BaseRefundResponse refundResponse = BaseRefundResponse.fromReference(request.getRefundExternalId(), WORLDPAY);
            return GatewayRefundResponse.fromBaseRefundResponse(refundResponse, PENDING);
        } catch (GatewayException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private GatewayOrder buildRefundOrder(RefundGatewayRequest request) {
        return aWorldpayRefundOrderRequestBuilder()
                .withReference(request.getRefundExternalId())
                .withMerchantCode(AuthUtil.getWorldpayMerchantCode(request.getGatewayCredentials(), request.getAuthorisationMode(), request.isForRecurringPayment()))
                .withAmount(request.getAmount())
                .withTransactionId(request.getTransactionId())
                .build();
    }
}
