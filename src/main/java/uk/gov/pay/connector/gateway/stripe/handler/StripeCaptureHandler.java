package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;

import javax.ws.rs.core.Response;
import java.net.URI;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.model.OrderRequestType.CAPTURE;

public class StripeCaptureHandler implements CaptureHandler {

    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    public StripeCaptureHandler(StripeGatewayClient client,
                                StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    @Override
    public GatewayResponse capture(CaptureGatewayRequest request) {

        String url = stripeGatewayConfig.getUrl() + "/v1/charges/" + request.getTransactionId() + "/capture";

        Response captureResponse = client.postRequest(
                request.getGatewayAccount(),
                CAPTURE,
                URI.create(url),
                StringUtils.EMPTY,
                StripeAuthUtil.getAuthHeaderValue(stripeGatewayConfig),
                APPLICATION_FORM_URLENCODED_TYPE);

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        if (StripeErrorHandler.hasClientError(captureResponse)) {
            StripeErrorResponse stripeErrorResponse = StripeErrorHandler.toErrorResponse(captureResponse);
            GatewayError gatewayError = GatewayError.unexpectedStatusCodeFromGateway(stripeErrorResponse.getError().getMessage());
            return responseBuilder.withGatewayError(gatewayError).build();
        }

        return responseBuilder.withResponse(StripeCaptureResponse.of(captureResponse)).build();
    }
}
