package uk.gov.pay.connector.gateway.stripe.handler;

import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.OrderRequestType;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.json.StripeCaptureResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED_TYPE;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;

public class StripeCaptureHandler implements CaptureHandler {

    private static final Logger logger = LoggerFactory.getLogger(StripeCaptureHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;

    public StripeCaptureHandler(GatewayClient client,
                                StripeGatewayConfig stripeGatewayConfig,
                                JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    @Override
    public CaptureResponse capture(CaptureGatewayRequest request) {

        String transactionId = request.getTransactionId();
        GatewayAccountEntity gatewayAccount = request.getGatewayAccount();

        try {
            StripeCaptureResponse stripeCaptureResponse = capture(transactionId, gatewayAccount, request.getExternalId());
            Optional<Long> maybeFee = calculateFee(request.getAmount(), stripeCaptureResponse);
            Long netTransferAmount = maybeFee
                    .map(fee -> calculateNetTransferAmount(request.getAmount(), fee))
                    .orElse(request.getAmount());
            transferNetAmount(request, gatewayAccount, netTransferAmount);

            return new CaptureResponse(stripeCaptureResponse.getId(), COMPLETE, maybeFee.orElse(null));
        } catch (GatewayErrorException e) {

            if (e.getFamily() == CLIENT_ERROR) {
                StripeErrorResponse stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                logger.error("Capture failed for transaction id {}. Failure code from Stripe: {}, failure message from Stripe: {}. External Charge id: {}. Response code from Stripe: {}",
                        transactionId, stripeErrorResponse.getError().getCode(), stripeErrorResponse.getError().getMessage(), request.getExternalId(), e.getStatus());

                return new CaptureResponse(new GatewayError(stripeErrorResponse.toString(), ErrorType.GENERIC_GATEWAY_ERROR), stripeErrorResponse.toString());
            }

            if (e.getFamily() == SERVER_ERROR) {
                logger.error("Capture failed for transaction id {}. Reason: {}. Status code from Stripe: {}. Charge External Id: {}",
                        transactionId, e.getMessage(), e.getStatus(), request.getExternalId());
                GatewayError gatewayError = gatewayConnectionError("An internal server error occurred when capturing charge_external_id: " + request.getExternalId());
                return CaptureResponse.fromGatewayError(gatewayError);
            }

            logger.info("Unrecognised response status during capture. charge_external_id={}, status={}, response={}",
                    request.getExternalId(), e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status during capture.");

        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        } 
    }

    private void transferNetAmount(CaptureGatewayRequest request, GatewayAccountEntity gatewayAccount, Long netTransferAmount) throws GatewayException {
        postToStripe(
                "/v1/transfers",
                getTransferPayload(netTransferAmount, request),
                gatewayAccount,
                request.getExternalId());
    }

    private StripeCaptureResponse capture(String stripeChargeId, GatewayAccountEntity gatewayAccount, String payChargeId) throws GatewayException {
        String captureResponse = postToStripe(
                "/v1/charges/" + stripeChargeId + "/capture",
                getCapturePayload(),
                gatewayAccount,
                payChargeId);
        return jsonObjectMapper.getObject(captureResponse, StripeCaptureResponse.class);
    }

    private Optional<Long> calculateFee(Long grossChargeAmount, StripeCaptureResponse stripeCaptureResponse) {
        if (stripeGatewayConfig.isCollectFee()) {
            Double additionalFee = Math.ceil((stripeGatewayConfig.getFeePercentage()/100) * grossChargeAmount);
            return Optional.of(stripeCaptureResponse.getFee() + additionalFee.longValue());
        }
        
        return Optional.empty();
    }
    
    private Long calculateNetTransferAmount(Long captureAmount, Long fee) {
        return captureAmount - fee;
    }

    private String getTransferPayload(Long netTransferAmount, CaptureGatewayRequest request) {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("amount", String.valueOf(netTransferAmount)));
        params.add(new BasicNameValuePair("destination", request.getGatewayAccount().getCredentials().get("stripe_account_id")));
        params.add(new BasicNameValuePair("currency", "gbp"));
        params.add(new BasicNameValuePair("source_transaction", request.getTransactionId()));
        params.add(new BasicNameValuePair("source_transaction", request.getTransactionId()));
        params.add(new BasicNameValuePair("expand[]", "balance_transaction"));
        params.add(new BasicNameValuePair("expand[]", "destination_payment"));

        return URLEncodedUtils.format(params, UTF_8);
    }
    
    private String getCapturePayload() {
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("expand[]", "balance_transaction"));

        return URLEncodedUtils.format(params, UTF_8);
    }

    private String postToStripe(String path, String payload, GatewayAccountEntity gatewayAccount, String idempotencyKey)
            throws GatewayException {
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Idempotency-Key", idempotencyKey);
        headers.putAll(AuthUtil.getStripeAuthHeader(stripeGatewayConfig, gatewayAccount.isLive()));
                
        return client.postRequestFor(

                URI.create(stripeGatewayConfig.getUrl() + path),
                gatewayAccount,
                new GatewayOrder(OrderRequestType.CAPTURE, payload, APPLICATION_FORM_URLENCODED_TYPE),
                headers)
                .getEntity();
    }
}
