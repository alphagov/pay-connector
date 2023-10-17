package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.gson.JsonObject;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeError;
import com.stripe.net.ApiResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.util.StripeFeeCalculator;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.json.StripeSearchTransfersResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripeTransfer;
import uk.gov.pay.connector.gateway.stripe.request.StripeGetPaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeSearchTransfersRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferOutRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.gateway.CaptureResponse.ChargeState.COMPLETE;
import static uk.gov.pay.connector.gateway.CaptureResponse.fromBaseCaptureResponse;
import static uk.gov.pay.connector.gateway.model.GatewayError.gatewayConnectionError;

public class StripeCaptureHandler implements CaptureHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeCaptureHandler.class);

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
        String transactionId = request.getGatewayTransactionId();

        try {
            List<Fee> feeList = doCaptureAndTransferIfNotPreviouslySucceeded(request);
            return new CaptureResponse(transactionId, COMPLETE, feeList);
        } catch (GatewayErrorException e) {

            if (e.getFamily() == CLIENT_ERROR) {
                final JsonObject jsonObject = ApiResource.GSON.fromJson(e.getResponseFromGateway(), JsonObject.class).getAsJsonObject("error");
                final StripeError stripeErrorResponse = ApiResource.GSON.fromJson(jsonObject, StripeError.class);
                
                String errorCode = stripeErrorResponse.getCode();
                String errorMessage = stripeErrorResponse.getMessage();
                LOGGER.warn("Capture failed for transaction id {}. Failure code from Stripe: {}, failure message from " +
                                "Stripe: {}. External Charge id: {}. Response code from Stripe: {}",
                        transactionId, errorCode, errorMessage, request.getExternalId(), e.getStatus());

                return fromBaseCaptureResponse(new StripeCaptureResponse(transactionId, errorCode, errorMessage), null);
            }

            if (e.getFamily() == SERVER_ERROR) {
                LOGGER.warn("Capture failed for transaction id {}. Reason: {}. Status code from Stripe: {}. Charge External Id: {}",
                        transactionId, e.getMessage(), e.getStatus(), request.getExternalId());
                GatewayError gatewayError = gatewayConnectionError("An internal server error occurred when capturing charge_external_id: " + request.getExternalId());
                return CaptureResponse.fromGatewayError(gatewayError);
            }

            LOGGER.error("Unrecognised response status during capture. charge_external_id={}, status={}, response={}",
                    request.getExternalId(), e.getStatus(), e.getResponseFromGateway());
            throw new RuntimeException("Unrecognised response status during capture.");

        } catch (GatewayException e) {
            return CaptureResponse.fromGatewayError(e.toGatewayError());
        }
    }

    private List<Fee> doCaptureAndTransferIfNotPreviouslySucceeded(CaptureGatewayRequest request) throws GatewayException {
        if (request.isCaptureRetry()) {
            Charge stripeCharge = queryStripeCharge(request);
            if (Boolean.TRUE.equals(stripeCharge.getCaptured())) {
                LOGGER.info("Charge already captured with Stripe on a previous attempt");
                return transferToConnectAccount(request, stripeCharge, true);
            }
        }

        LOGGER.info("Making request to Stripe to capture charge");
        Charge capturedCharge = captureWithPaymentIntentAPI(request);
        return transferToConnectAccount(request, capturedCharge, false);
    }

    private Charge queryStripeCharge(CaptureGatewayRequest request) throws GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException, GatewayErrorException {
        var getPaymentIntentRequest = new StripeGetPaymentIntentRequest(request.getGatewayAccount(), stripeGatewayConfig, request.getGatewayTransactionId());
        String rawResponse = client.getRequestFor(getPaymentIntentRequest).getEntity();
        PaymentIntent paymentIntent = ApiResource.GSON.fromJson(rawResponse, PaymentIntent.class);

        return getStripeChargeFromPaymentIntent(paymentIntent);
    }

    private Charge getStripeChargeFromPaymentIntent(PaymentIntent paymentIntent) throws GatewayException.GenericGatewayException {
        List<Charge> stripeCharges = paymentIntent.getCharges().getData();

        if (stripeCharges.size() != 1) {
            throw new GatewayException.GenericGatewayException(
                String.format("Expected exactly one charge associated with payment intent %s, got %s", paymentIntent.getId(), stripeCharges.size()));
        }
        return stripeCharges.get(0);
    }

    private List<Fee> transferToConnectAccount(CaptureGatewayRequest request, Charge capturedCharge, boolean checkForExistingTransfer) 
            throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {

        Long stripeFee = Optional.ofNullable(capturedCharge.getBalanceTransactionObject()).map(BalanceTransaction::getFee)
                .orElseThrow(() -> new GatewayException.GenericGatewayException(
                        String.format("Fee not found on Stripe charge %s when attempting to capture payment", capturedCharge.getId())));
        List<Fee> feeList = generateFeeList(request, stripeFee);
        Long processingFee = StripeFeeCalculator.getTotalFeeAmount(feeList);
        Long netTransferAmount = request.getAmount() - processingFee;

        if (checkForExistingTransfer) {
            StripeSearchTransfersRequest searchTransfersRequest = new StripeSearchTransfersRequest(
                    request.getGatewayAccount(), stripeGatewayConfig, request.getExternalId());
            
            String rawResponse = client.getRequestFor(searchTransfersRequest).getEntity();
            StripeSearchTransfersResponse transfersResponse = jsonObjectMapper.getObject(rawResponse,
                    StripeSearchTransfersResponse.class);
            
            if (!transfersResponse.getTransfers().isEmpty()) {
                LOGGER.info("Transfer of captured funds previously succeeded.");
                return feeList;
            }
        }

        LOGGER.info("Making request to Stripe to transfer funds for captured amount to connect account");
        transferToConnectAccount(request, netTransferAmount, capturedCharge.getId());
        return feeList;
    }

    private Charge captureWithPaymentIntentAPI(CaptureGatewayRequest request) throws GatewayException {
        String captureResponse = client.postRequestFor(StripePaymentIntentCaptureRequest.of(request, stripeGatewayConfig)).getEntity();
        PaymentIntent stripeCaptureResponse = ApiResource.GSON.fromJson(captureResponse, PaymentIntent.class);
        Charge stripeCharge = getStripeChargeFromPaymentIntent(stripeCaptureResponse);

        LOGGER.info("Captured charge id {} with platform account - stripe capture id {}",
                request.getExternalId(),
                stripeCharge.getId()
        );

        return stripeCharge;
    }

    private void transferToConnectAccount(CaptureGatewayRequest request, Long netTransferAmount, String stripeChargeId) throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        String transferResponse = client.postRequestFor(StripeTransferOutRequest.of(netTransferAmount.toString(), stripeChargeId, request, stripeGatewayConfig)).getEntity();
        StripeTransfer stripeTransfer = jsonObjectMapper.getObject(transferResponse, StripeTransfer.class);
        LOGGER.info("In capturing charge id {}, transferred net amount {} - transfer id {} -  to Stripe Connect account id {} in transfer group {}",
                request.getExternalId(),
                stripeTransfer.getAmount(),
                stripeTransfer.getId(),
                stripeTransfer.getDestinationStripeAccountId(),
                stripeTransfer.getStripeTransferGroup()
        );
    }

    private List<Fee> generateFeeList(CaptureGatewayRequest request, Long stripeFee) {
        if (stripeGatewayConfig.isCollectFee()) {
            return StripeFeeCalculator.getFeeList(stripeFee, request, stripeGatewayConfig.getFeePercentage(),
                    stripeGatewayConfig.getRadarFeeInPence(), stripeGatewayConfig.getThreeDsFeeInPence());
        }
        return List.of();
    }
}
