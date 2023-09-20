package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.gson.Gson;
import com.stripe.model.Transfer;
import com.stripe.net.ApiResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.RefundGatewayRequest;
import uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripeRefund;
import uk.gov.pay.connector.gateway.stripe.request.StripeGetPaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeRefundRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeRefundResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;
import static uk.gov.pay.connector.gateway.model.response.GatewayRefundResponse.fromBaseRefundResponse;

public class StripeRefundHandler {
    private static final Logger logger = LoggerFactory.getLogger(StripeRefundHandler.class);
    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private JsonObjectMapper jsonObjectMapper;

    public StripeRefundHandler(
            GatewayClient client,
            StripeGatewayConfig stripeGatewayConfig,
            JsonObjectMapper jsonObjectMapper
    ) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public GatewayRefundResponse refund(RefundGatewayRequest request) {
        String stripeChargeId;
        try {
            if (usePaymentIntent(request)) {
                StripePaymentIntent stripePaymentIntent = getPaymentIntent(request);
                stripeChargeId = stripePaymentIntent.getCharge()
                        .map(StripeCharge::getId)
                        .orElseThrow(() -> new GatewayException.GenericGatewayException(
                                String.format("Stripe charge not found for payment intent id %s", request.getTransactionId()))
                        );
            } else {
                stripeChargeId = request.getTransactionId();
            }
            StripeRefund refundResponse = refundCharge(request, stripeChargeId);
            transferFromConnectAccount(request, stripeChargeId);

            return fromBaseRefundResponse(StripeRefundResponse.of(refundResponse.getId()), GatewayRefundResponse.RefundState.COMPLETE);
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

            logger.error("Unrecognised response status when refunding. refund_external_id={}, status={}, response={}",
                    request.getRefundExternalId(), e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status when refunding.");
        } catch (GatewayException e) {
            logger.error("Refund failed for refund gateway request {}. GatewayException: {}.", request, e);
            return GatewayRefundResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private boolean usePaymentIntent(RefundGatewayRequest request) {
        return request.getTransactionId().startsWith("pi_");
    }
    
    private StripePaymentIntent getPaymentIntent(RefundGatewayRequest request) throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        final String rawResponse = client.getRequestFor(StripeGetPaymentIntentRequest.of(request, stripeGatewayConfig)).getEntity();
        return jsonObjectMapper.getObject(rawResponse, StripePaymentIntent.class);
    }

    private StripeRefund refundCharge(RefundGatewayRequest request, String stripeChargeId) throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        StripeRefundRequest stripeRefundRequest = StripeRefundRequest.of(request, stripeChargeId, stripeGatewayConfig);
        final String refundResponse = client.postRequestFor(stripeRefundRequest).getEntity();
        StripeRefund refund = jsonObjectMapper.getObject(refundResponse, StripeRefund.class);
        logger.info("As part of refund {} to refund charge id {} refunded stripe charge id {}",
                request.getTransactionId(),
                request.getChargeExternalId(),
                request.getRefundExternalId()
        );

        return refund;

    }

    private void transferFromConnectAccount(RefundGatewayRequest request, String stripeChargeId) throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        String transferResponse = client.postRequestFor(StripeTransferInRequest.createRefundTransferRequest(request, stripeChargeId, stripeGatewayConfig)).getEntity();
        Transfer stripeTransfer = ApiResource.GSON.fromJson(transferResponse, Transfer.class);
        logger.info("As part of refund {} refunding charge id {}, transferred net amount {} - transfer id {} -  from Stripe Connect account id {} in transfer group {}",
                request.getRefundExternalId(),
                request.getChargeExternalId(),
                stripeTransfer.getAmount(),
                stripeTransfer.getId(),
                stripeTransfer.getDestination(),
                stripeTransfer.getTransferGroup()
        );
    }
}
