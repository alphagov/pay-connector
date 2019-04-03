package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
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
            final String response = client.postRequestFor(URI.create(url),
                    gatewayAccount,
                    new GatewayOrder(OrderRequestType.REFUND, payload, APPLICATION_FORM_URLENCODED_TYPE),
                    getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive())).getEntity();
            String reference = jsonObjectMapper.getObject(response, Map.class).get("id").toString();
            return fromBaseRefundResponse(StripeRefundResponse.of(reference), GatewayRefundResponse.RefundState.COMPLETE);

        } catch (GatewayErrorException e) {

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse.Error error = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class).getError();
                logger.error("Refund failed for refund gateway request {}. Failure code from Stripe: {}, failure message from Stripe: {}. Response code from Stripe: {}",
                        request, error.getCode(), error.getMessage(), e.getStatus());

                return fromBaseRefundResponse(
                        StripeRefundResponse.of(error.getCode(), error.getMessage()),
                        GatewayRefundResponse.RefundState.ERROR);
            }
            
            if (e.getFamily() == SERVER_ERROR) {
                logger.error("Refund failed for refund gateway request {}. Reason: {}. Status code from Stripe: {}.", request, e.getMessage(), e.getStatus());
                GatewayError gatewayError = gatewayConnectionError("An internal server error occurred while refunding Transaction id: " + request.getTransactionId());
                return GatewayRefundResponse.fromGatewayError(gatewayError);
            }

            logger.info("Unrecognised response status when refunding. refund_external_id={}, status={}, response={}",
                    request.getRefundExternalId(), e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when refunding.");
            
        } catch (GatewayException e) {
            logger.error("Refund failed for refund gateway request {}. GatewayException: {}.", request, e);
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
