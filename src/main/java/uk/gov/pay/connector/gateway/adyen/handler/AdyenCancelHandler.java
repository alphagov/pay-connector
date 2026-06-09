package uk.gov.pay.connector.gateway.adyen.handler;

import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenCancelRequest;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getCancelUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;
import static uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus.SUBMITTED;

public class AdyenCancelHandler {

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;
    private final JsonObjectMapper jsonObjectMapper;

    public AdyenCancelHandler(GatewayClient gatewayClient,
                              AdyenGatewayConfig adyenGatewayConfig,
                              AdyenRequestFactory adyenRequestFactory,
                              JsonObjectMapper jsonObjectMapper) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.adyenRequestFactory = adyenRequestFactory;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        GatewayResponseBuilder<BaseCancelResponse> responseBuilder = GatewayResponseBuilder.responseBuilder();
        var cancelRequest = new AdyenCancelRequest(
                getCancelUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.isLiveAccount(), request.getRequestType(), request.getExternalChargeId()),
                adyenRequestFactory.createPaymentCancelRequest(request),
                request.getGatewayAccount().getType(),
                jsonObjectMapper);
        try {
            gatewayClient.postRequestFor(cancelRequest);
        } catch (GatewayException e) {
            return responseBuilder.withGatewayError(e.toGatewayError()).build();
        }
        return responseBuilder.withResponse(new BaseCancelResponse() {
            @Override
            public String getTransactionId() {
                return "";
            }

            @Override
            public CancelStatus cancelStatus() {
                return SUBMITTED;
            }

            @Override
            public String getErrorCode() {
                return "";
            }

            @Override
            public String getErrorMessage() {
                return "";
            }
        }).build();
    }
}
