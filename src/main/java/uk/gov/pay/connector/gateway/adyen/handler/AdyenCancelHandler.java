package uk.gov.pay.connector.gateway.adyen.handler;

import io.dropwizard.jackson.Jackson;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.adyen.AdyenRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.AdyenCancelRequest;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getCancelUrl;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenRequestUtil.getHeaders;
import static uk.gov.pay.connector.gateway.model.response.BaseCancelResponse.CancelStatus.SUBMITTED;

public class AdyenCancelHandler {

    private final GatewayClient gatewayClient;
    private final AdyenGatewayConfig adyenGatewayConfig;
    private final AdyenRequestFactory adyenRequestFactory;

    public AdyenCancelHandler(GatewayClient gatewayClient,
                              AdyenGatewayConfig adyenGatewayConfig,
                              AdyenRequestFactory adyenRequestFactory) {
        this.gatewayClient = gatewayClient;
        this.adyenGatewayConfig = adyenGatewayConfig;
        this.adyenRequestFactory = adyenRequestFactory;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        var responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();
        var cancelRequest = new AdyenCancelRequest(
                getCancelUrl(adyenGatewayConfig, request),
                getHeaders(adyenGatewayConfig, request.isLiveAccount()),
                adyenRequestFactory.createPaymentCancelRequest(request),
                request.getGatewayAccount().getType(),
                new JsonObjectMapper(Jackson.newObjectMapper()));
        try {
            gatewayClient.postRequestFor(cancelRequest);
        } catch (GatewayException.GenericGatewayException e) {
            throw new RuntimeException(e);
        } catch (GatewayException.GatewayErrorException e) {
            throw new RuntimeException(e);
        } catch (GatewayException.GatewayConnectionTimeoutException e) {
            throw new RuntimeException(e);
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
