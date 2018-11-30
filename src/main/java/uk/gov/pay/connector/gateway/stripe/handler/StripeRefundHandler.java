package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.common.collect.ImmutableMap;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseRefundResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeRefundResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil.getAuthHeaderValue;

public class StripeRefundHandler {
    private static final Logger logger = LoggerFactory.getLogger(StripeRefundHandler.class);
    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    public StripeRefundHandler(StripeGatewayClient client, StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    public GatewayResponse<BaseRefundResponse> refund(RefundGatewayRequest request) {
        String url = stripeGatewayConfig.getUrl() + "/v1/refunds";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        try {
            String payload = URLEncodedUtils.format(buildPayload(request.getTransactionId(), request.getAmount()), UTF_8);
            final Response response = client.postRequest(
                    URI.create(url),
                    payload,
                    ImmutableMap.of(AUTHORIZATION, getAuthHeaderValue(stripeGatewayConfig)),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.cancel", gatewayAccount.getGatewayName(), gatewayAccount.getType()));

            return responseBuilder.withResponse(StripeRefundResponse.of(response)).build();

        } catch (GatewayClientException e) {

            Response response = e.getResponse();
            StripeErrorResponse stripeErrorResponse = response.readEntity(StripeErrorResponse.class);
            String errorId = UUID.randomUUID().toString();
            logger.error("Refund failed for transaction id {}. Failure code from Stripe: {}, failure message from Stripe: {}. ErrorId: {}. Response code from Stripe: {}",
                    request.getTransactionId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), errorId, response.getStatus());
            GatewayError gatewayError = genericGatewayError(stripeErrorResponse.getError().getMessage());
            return responseBuilder.withGatewayError(gatewayError).build();

        } catch (GatewayException e) {

            return responseBuilder.withGatewayError(GatewayError.of(e)).build();

        } catch (DownstreamException e) {
            String errorId = UUID.randomUUID().toString();
            logger.error("Refund failed for transaction id {}. Reason: {}. Status code from Stripe: {}. ErrorId: {}",
                    request.getTransactionId(), e.getMessage(), e.getStatusCode(), errorId);
            GatewayError gatewayError = unexpectedStatusCodeFromGateway("An internal server error occurred. ErrorId: " + errorId);
            return responseBuilder.withGatewayError(gatewayError).build();
        }
    }

    /**
     * If there is no <code>amount</code> specified in the payload
     * then this is a full refund
     * <p>
     * Payload example <code>
     * {
     * "charge": "abc",
     * "refund_application_fee": true,
     * "reverse_transfer": true
     * }
     * </code>
     *
     * @return List<BasicNameValuePair> representing all the parameters
     * required for this refund
     */
    private List<BasicNameValuePair> buildPayload(String chargeGatewayId, String amount) {
        List<BasicNameValuePair> payload = new ArrayList<>();
        payload.add(new BasicNameValuePair("charge", chargeGatewayId));
        payload.add(new BasicNameValuePair("amount", amount));
        payload.add(new BasicNameValuePair("refund_application_fee", "true"));
        payload.add(new BasicNameValuePair("reverse_transfer", "true"));

        return payload;
    }
}
