package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.common.collect.ImmutableMap;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClientResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.fromBaseRefundResponse;
import static uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil.getAuthHeaderValue;

public class StripeRefundHandler {
    private static final Logger logger = LoggerFactory.getLogger(StripeRefundHandler.class);
    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private JsonObjectMapper jsonObjectMapper;

    public StripeRefundHandler(StripeGatewayClient client, StripeGatewayConfig stripeGatewayConfig, JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        String url = stripeGatewayConfig.getUrl() + "/v1/refunds";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        try {
            String payload = URLEncodedUtils.format(buildPayload(request.getTransactionId(), request.getAmount()), UTF_8);
            final String response = client.postRequest(
                    URI.create(url),
                    payload,
                    ImmutableMap.of(AUTHORIZATION, getAuthHeaderValue(stripeGatewayConfig)),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.refund", gatewayAccount.getGatewayName(), gatewayAccount.getType()));
            String reference = jsonObjectMapper.getObject(response, Map.class).get("id").toString();
            return fromBaseRefundResponse(StripeRefundResponse.of(reference), GatewayRefundResponse.RefundState.COMPLETE);

        } catch (GatewayClientException e) {

            StripeGatewayClientResponse response = e.getResponse();
            StripeErrorResponse.Error error = jsonObjectMapper.getObject(response.getPayload(), StripeErrorResponse.class).getError();
            logger.error("Refund failed for refund gateway request {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                    request, error.getCode(), error.getMessage(), response.getStatus());

            return fromBaseRefundResponse(
                    StripeRefundResponse.of(error.getCode(), error.getMessage()),
                    GatewayRefundResponse.RefundState.ERROR);
        } catch (GatewayException e) {
            logger.error("Refund failed for refund gateway request {}. GatewayException: {}.", request, e);
            return GatewayRefundResponse.fromGatewayError(GatewayError.of(e));
        } catch (DownstreamException e) {
            logger.error("Refund failed for refund gateway request {}. Reason: {}. Status code from Stripe: {}.", request, e.getMessage(), e.getStatusCode());
            GatewayError gatewayError = unexpectedStatusCodeFromGateway("An internal server error occurred while refunding Transaction id: " + request.getTransactionId());
            return GatewayRefundResponse.fromGatewayError(gatewayError);
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
