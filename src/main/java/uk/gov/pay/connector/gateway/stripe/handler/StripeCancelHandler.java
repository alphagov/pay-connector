package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.common.collect.ImmutableMap;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CancelGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCancelResponse;
import uk.gov.pay.connector.gateway.model.response.BaseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClientResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.util.NoLiveTokenConfiguredException;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import javax.ws.rs.WebApplicationException;
import java.net.URI;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.GatewayError.genericGatewayError;
import static uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil.getAuthHeaderValue;

public class StripeCancelHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCancelHandler.class);

    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private JsonObjectMapper jsonObjectMapper;

    public StripeCancelHandler(StripeGatewayClient client, StripeGatewayConfig stripeGatewayConfig, JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayResponse<BaseCancelResponse> cancel(CancelGatewayRequest request) {
        String url = stripeGatewayConfig.getUrl() + "/v1/refunds";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        GatewayResponse.GatewayResponseBuilder<BaseResponse> responseBuilder = GatewayResponse.GatewayResponseBuilder.responseBuilder();

        try {
            String payload = URLEncodedUtils.format(singletonList(new BasicNameValuePair("charge", request.getTransactionId())), UTF_8);
            client.postRequest(
                    URI.create(url),
                    payload,
                    ImmutableMap.of(AUTHORIZATION, getAuthHeaderValue(stripeGatewayConfig, gatewayAccount.isLive())),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.cancel", gatewayAccount.getGatewayName(), gatewayAccount.getType()));

            return responseBuilder.withResponse(new BaseCancelResponse() {

                private final String transactionId = randomUUID().toString();

                @Override
                public String getErrorCode() {
                    return null;
                }

                @Override
                public String getErrorMessage() {
                    return null;
                }

                @Override
                public String getTransactionId() {
                    return transactionId;
                }

                @Override
                public CancelStatus cancelStatus() {
                    return CancelStatus.CANCELLED;
                }
            }).build();

        } catch (GatewayClientException e) {

            StripeGatewayClientResponse response = e.getResponse();
            StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(response.getPayload(), StripeErrorResponse.class);
            logger.error("Cancel failed for gateway transaction id {}. Failure code from Stripe: {}, failure message from Stripe: {}. Charge External Id: {}. Response code from Stripe: {}",
                    request.getTransactionId(), stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), request.getExternalChargeId(), response.getStatus());
            GatewayError gatewayError = genericGatewayError(stripeErrorResponse.getError().getMessage());

            return responseBuilder.withGatewayError(gatewayError).build();
        } catch (GatewayException e) {
            return responseBuilder.withGatewayError(GatewayError.of(e)).build();
        } catch (DownstreamException e) {
            logger.error("Cancel failed for transaction id {}. Reason: {}. Status code from Stripe: {}. Charge External Id: {}",
                    request.getTransactionId(), e.getMessage(), e.getStatusCode(), request.getExternalChargeId());
            GatewayError gatewayError = gatewayConnectionError("An internal server error occurred while cancelling external charge id: " + request.getExternalChargeId());
            return responseBuilder.withGatewayError(gatewayError).build();
        } catch (NoLiveTokenConfiguredException e) {
            logger.error("Could not cancel charge external id {}. Reason: No live token configured for gateway account {}.",
                    request.getExternalChargeId(), request.getGatewayAccount().getId());
            throw new WebApplicationException("There was an internal server error cancelling charge external id: " + request.getExternalChargeId());
        }
    }
}
