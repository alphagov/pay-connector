package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.core.MediaType;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.fromBaseRefundResponse;
import static uk.gov.pay.connector.gateway.util.AuthUtil.getStripeAuthHeader;

public class StripeRefundHandler {
    private static final Logger logger = LoggerFactory.getLogger(StripeRefundHandler.class);
    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private JsonObjectMapper jsonObjectMapper;

    public StripeRefundHandler(GatewayClient client, StripeGatewayConfig stripeGatewayConfig, JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        String url = stripeGatewayConfig.getUrl() + "/v1/refunds";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        try {
            String payload = URLEncodedUtils.format(buildPayload(request.getTransactionId(), request.getAmount()), UTF_8);
            final GatewayClient.Response response = client.postRequestFor(
                    URI.create(url), 
                    gatewayAccount,
                    new GatewayOrder(OrderRequestType.REFUND, payload, MediaType.APPLICATION_FORM_URLENCODED_TYPE),
                    getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));
            String reference = jsonObjectMapper.getObject(response.getEntity(), Map.class).get("id").toString();
            return fromBaseRefundResponse(StripeRefundResponse.of(reference), GatewayRefundResponse.RefundState.COMPLETE);

        } catch (GatewayErrorException e) {
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        } 
    }

    private List<BasicNameValuePair> buildPayload(String chargeGatewayId, String amount) {
        List<BasicNameValuePair> payload = new ArrayList<>();
        payload.add(new BasicNameValuePair("charge", chargeGatewayId));
        payload.add(new BasicNameValuePair("amount", amount));
        payload.add(new BasicNameValuePair("refund_application_fee", "true"));
        payload.add(new BasicNameValuePair("reverse_transfer", "true"));

        return payload;
    }
}
