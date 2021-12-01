package uk.gov.pay.connector.gateway.stripe.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.gateway.CaptureHandler;
import uk.gov.pay.connector.gateway.CaptureResponse;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripeErrorResponse;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.json.StripeTransferResponse;
import uk.gov.pay.connector.gateway.stripe.request.StripePaymentIntentCaptureRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferOutRequest;
import uk.gov.pay.connector.gateway.stripe.response.StripeCaptureResponse;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SERVER_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
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
        String transactionId = request.getTransactionId();

        try {
            StripeCharge capturedCharge;

            capturedCharge = captureWithPaymentIntentAPI(request);

            boolean is3dsUsed = request.getEvents()
                    .stream()
                    .anyMatch(chargeEventEntity -> chargeEventEntity.getStatus().equals(AUTHORISATION_3DS_REQUIRED));
            
            Optional<Long> processingFee = capturedCharge.getFee()
                    .flatMap(fee -> calculateProcessingFee(request.getCreatedDate(), request.getAmount(), fee, is3dsUsed));

            Long netTransferAmount = processingFee
                    .map(fee -> request.getAmount() - fee)
                    .orElse(request.getAmount());

            transferToConnectAccount(request, netTransferAmount, capturedCharge.getId());

            return new CaptureResponse(transactionId, COMPLETE, processingFee.orElse(null));
        } catch (GatewayErrorException e) {

            if (e.getFamily() == CLIENT_ERROR) {
                var stripeErrorResponse = jsonObjectMapper.getObject(e.getResponseFromGateway(), StripeErrorResponse.class);
                String errorCode = stripeErrorResponse.getError().getCode();
                String errorMessage = stripeErrorResponse.getError().getMessage();
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

    private StripeCharge captureWithPaymentIntentAPI(CaptureGatewayRequest request) throws GatewayException {
        String captureResponse = client.postRequestFor(StripePaymentIntentCaptureRequest.of(request, stripeGatewayConfig)).getEntity();
        StripePaymentIntent stripeCaptureResponse = jsonObjectMapper.getObject(captureResponse, StripePaymentIntent.class);
        List<StripeCharge> stripeCharges = stripeCaptureResponse.getChargesCollection().getCharges();

        if (stripeCharges.size() != 1) {
            throw new GatewayErrorException(
                    String.format("Expected exactly one charge associated with payment intent %s, got %s", request.getTransactionId(), stripeCharges.size()));
        }

        StripeCharge stripeCharge = stripeCharges.get(0);

        LOGGER.info("Captured charge id {} with platform account - stripe capture id {}",
                request.getExternalId(),
                stripeCharge.getId()
        );

        return stripeCharge;
    }

    private void transferToConnectAccount(CaptureGatewayRequest request, Long netTransferAmount, String stripeChargeId) throws GatewayException.GenericGatewayException, GatewayErrorException, GatewayException.GatewayConnectionTimeoutException {
        String transferResponse = client.postRequestFor(StripeTransferOutRequest.of(netTransferAmount.toString(), stripeChargeId, request, stripeGatewayConfig)).getEntity();
        StripeTransferResponse stripeTransferResponse = jsonObjectMapper.getObject(transferResponse, StripeTransferResponse.class);
        LOGGER.info("In capturing charge id {}, transferred net amount {} - transfer id {} -  to Stripe Connect account id {} in transfer group {}",
                request.getExternalId(),
                stripeTransferResponse.getAmount(),
                stripeTransferResponse.getId(),
                stripeTransferResponse.getDestinationStripeAccountId(),
                stripeTransferResponse.getStripeTransferGroup()
        );
    }

    private Optional<? extends Long> calculateProcessingFee(Instant createdDate, Long grossChargeAmount, Long stripeFee, boolean is3dsUsed) {
        if (stripeGatewayConfig.isCollectFee()) {

            Double feePercentage;
            if (createdDate.isBefore(stripeGatewayConfig.getFeePercentageV2Date())) {
                feePercentage = stripeGatewayConfig.getFeePercentage();
            } else {
                feePercentage = stripeGatewayConfig.getFeePercentageV2();
            }

            Double platformFee = Math.ceil((feePercentage / 100) * grossChargeAmount);

            if (createdDate.isAfter(stripeGatewayConfig.getFeePercentageV2Date())) {
                platformFee += stripeGatewayConfig.getRadarFeeInPence();
                if (is3dsUsed) {
                    platformFee += stripeGatewayConfig.getThreeDsFeeInPence();
                }
            }
            return Optional.of(stripeFee + platformFee.longValue());
        }

        return Optional.empty();
    }
}
