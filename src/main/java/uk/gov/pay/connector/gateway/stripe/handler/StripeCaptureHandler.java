package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;

public class StripeCaptureHandler implements CaptureHandler {

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;

    public StripeCaptureHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig, JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {

        String transactionId = request.getTransactionId();
        String url = stripeGatewayConfig.getUrl() + "/v1/charges/" + transactionId + "/capture";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        try {
            GatewayClient.Response response = client.postRequestFor(
                    URI.create(url), 
                    gatewayAccount,
                    new GatewayOrder(OrderRequestType.CAPTURE, StringUtils.EMPTY, APPLICATION_FORM_URLENCODED_TYPE),
                    AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));
            String stripeTransactionId = jsonObjectMapper.getObject(response.getEntity(), Map.class).get("id").toString();
            return fromBaseCaptureResponse(new StripeCaptureResponse(stripeTransactionId), COMPLETE);

        } catch (GatewayErrorException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }
}
