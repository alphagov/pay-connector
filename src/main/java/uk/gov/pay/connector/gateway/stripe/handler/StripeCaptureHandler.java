package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.Response;
import java.net.URI;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;

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
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();
        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        try {
            Response captureResponse = client.postRequest(
                    URI.create(url),
                    StringUtils.EMPTY,
                    ImmutableMap.of(AUTHORIZATION, StripeAuthUtil.getAuthHeaderValue(stripeGatewayConfig)),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.authorise.create_source",
                            gatewayAccount.getGatewayName(),
                            gatewayAccount.getType()));

            return responseBuilder.withResponse(StripeCaptureResponse.of(captureResponse)).build();

        } catch (GatewayClientException e) {
            GatewayError gatewayError = GatewayError.unexpectedStatusCodeFromGateway(e
                    .getResponse()
                    .readEntity(StripeErrorResponse.class)
                    .getError()
                    .getMessage());
            
            return responseBuilder.withGatewayError(gatewayError).build();
        }
    }
}
