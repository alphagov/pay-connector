package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClientResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.Map;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayDownstreamError;

public class StripeCaptureHandler implements CaptureHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCaptureHandler.class);

    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;

    public StripeCaptureHandler(StripeGatewayClient client,
                                StripeGatewayConfig stripeGatewayConfig,
                                JsonObjectMapper jsonObjectMapper) {
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
            String captureResponse = client.postRequest(
                    URI.create(url),
                    StringUtils.EMPTY,
                    AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.capture",
                            gatewayAccount.getGatewayName(),
                            gatewayAccount.getType()));
            String stripeTransactionId = jsonObjectMapper.getObject(captureResponse, Map.class).get("id").toString();
            return fromBaseCaptureResponse(new StripeCaptureResponse(stripeTransactionId), COMPLETE);

        } catch (GatewayClientException e) {

            StripeGatewayClientResponse response = e.getResponse();
            StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(response.getPayload(), StripeErrorResponse.class);
            String errorCode = stripeErrorResponse.getError().getCode();
            String errorMessage = stripeErrorResponse.getError().getMessage();
            logger.error("Capture failed for transaction id {}. Failure code from Stripe: {}, failure message from Stripe: {}. External Charge id: {}. Response code from Stripe: {}",
                    transactionId, errorCode, errorMessage, request.getExternalId(), response.getStatus());

            return fromBaseCaptureResponse(new StripeCaptureResponse(transactionId, errorCode, errorMessage), null);

        } catch (GatewayException e) {

            return CaptureResponse.fromGatewayError(GatewayError.of(e));

        } catch (DownstreamException e) {
            logger.error("Capture failed for transaction id {}. Reason: {}. Status code from Stripe: {}. Charge External Id: {}",
                    transactionId, e.getMessage(), e.getStatusCode(), request.getExternalId());
            GatewayError gatewayError = gatewayDownstreamError("An internal server error occurred when capturing charge_external_id: " + request.getExternalId());
            return CaptureResponse.fromGatewayError(gatewayError);
        }
    }
}
