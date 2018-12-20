package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.BaseCaptureResponse;
import uk.gov.pay.connector.gateway.stripe.DownstreamException;
import uk.gov.pay.connector.gateway.stripe.GatewayClientException;
import uk.gov.pay.connector.gateway.stripe.GatewayException;
import uk.gov.pay.connector.gateway.stripe.StripeGatewayClient;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.stripe.util.StripeAuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static javax.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;

public class StripeCaptureHandler implements CaptureHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCaptureHandler.class);
    
    private final StripeGatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;

    public StripeCaptureHandler(StripeGatewayClient client,
                                StripeGatewayConfig stripeGatewayConfig) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {

        String transactionId = request.getTransactionId();
        String url = stripeGatewayConfig.getUrl() + "/v1/charges/" + transactionId + "/capture";
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        try {
            Response captureResponse = client.postRequest(
                    URI.create(url),
                    StringUtils.EMPTY,
                    ImmutableMap.of(AUTHORIZATION, StripeAuthUtil.getAuthHeaderValue(stripeGatewayConfig)),
                    APPLICATION_FORM_URLENCODED_TYPE,
                    format("gateway-operations.%s.%s.capture",
                            gatewayAccount.getGatewayName(),
                            gatewayAccount.getType()));
            
            return fromBaseCaptureResponse(new StripeCaptureResponse((String) captureResponse.readEntity(Map.class).get("id")), COMPLETE);

        } catch (GatewayClientException e) {
            
            Response response = e.getResponse();
            StripeErrorResponse stripeErrorResponse = response.readEntity(StripeErrorResponse.class);
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
            GatewayError gatewayError = unexpectedStatusCodeFromGateway("An internal server error occurred when capturing charge_external_id: " + request.getExternalId());
            return CaptureResponse.fromGatewayError(gatewayError);
        }

    }
}
