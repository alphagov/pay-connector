package uk.gov.pay.connector.gateway.stripe.handler;

import com.google.gson.Gson;
import com.stripe.model.Transfer;
import com.stripe.net.ApiResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.StripeGatewayConfig;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.charge.util.StripeFeeCalculator;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.stripe.json.StripeCharge;
import uk.gov.pay.connector.gateway.stripe.json.StripePaymentIntent;
import uk.gov.pay.connector.gateway.stripe.request.StripeGetPaymentIntentRequest;
import uk.gov.pay.connector.gateway.stripe.request.StripeTransferInRequest;
import uk.gov.pay.connector.util.JsonObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class StripeFailedPaymentFeeCollectionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeFailedPaymentFeeCollectionHandler.class);

    private final GatewayClient client;
    private final StripeGatewayConfig stripeGatewayConfig;
    private final JsonObjectMapper jsonObjectMapper;

    public StripeFailedPaymentFeeCollectionHandler(
            GatewayClient client,
            StripeGatewayConfig stripeGatewayConfig,
            JsonObjectMapper jsonObjectMapper) {
        this.client = client;
        this.stripeGatewayConfig = stripeGatewayConfig;
        this.jsonObjectMapper = jsonObjectMapper;
    }

    public List<Fee> calculateAndTransferFees(ChargeEntity charge) throws GatewayException {
        // It is possible for no Stripe charge to exist for the payment intent if the 3DS attempt is failed. For now, we
        // are falling back on checking our internal charge events if there is no Stripe charge. This will however miss
        // cases where 3DS has been initiated but not completed, for which we might still be charged a fee. Ideally
        // we need a way to tell whether a 3DS attempt has been started from the Stripe API.
        boolean threeDsFeeApplicable = getStripeCharge(charge)
                .map(this::stripeChargeHas3dsAttempt)
                .orElseGet(() -> chargeHasEventsIndicating3dsWasCompleted(charge));

        List<Fee> fees = new ArrayList<>();
        fees.add(Fee.of(FeeType.RADAR, (long) stripeGatewayConfig.getRadarFeeInPence()));
        if (threeDsFeeApplicable) {
            fees.add(Fee.of(FeeType.THREE_D_S, (long) stripeGatewayConfig.getThreeDsFeeInPence()));
        }
        Long feeAmount = StripeFeeCalculator.getTotalFeeAmount(fees);

        transferFeeFromConnectAccount(feeAmount, charge);

        return fees;

    }

    private boolean stripeChargeHas3dsAttempt(StripeCharge stripeCharge) {
        return stripeCharge.getPaymentMethodDetails() != null &&
                stripeCharge.getPaymentMethodDetails().getCard() != null &&
                stripeCharge.getPaymentMethodDetails().getCard().getThreeDSecure() != null &&
                stripeCharge.getPaymentMethodDetails().getCard().getThreeDSecure().getAuthenticated();
    }

    private boolean chargeHasEventsIndicating3dsWasCompleted(ChargeEntity chargeEntity) {
        List<ChargeStatus> eventStatuses = chargeEntity.getEvents().stream().map(ChargeEventEntity::getStatus).collect(Collectors.toList());
        return eventStatuses.contains(ChargeStatus.AUTHORISATION_3DS_REQUIRED) &&
                (eventStatuses.contains(ChargeStatus.AUTHORISATION_REJECTED) || eventStatuses.contains(ChargeStatus.AUTHORISATION_SUCCESS));
    }

    private Optional<StripeCharge> getStripeCharge(ChargeEntity charge) throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        StripePaymentIntent paymentIntent = getPaymentIntent(charge);

        List<StripeCharge> charges = paymentIntent.getChargesCollection().getCharges();
        if (charges.size() > 1) {
            throw new RuntimeException("Expected at most 1 Charge for PaymentIntent, found " + charges.size());
        }
        return charges.stream().findFirst();
    }

    private StripePaymentIntent getPaymentIntent(ChargeEntity charge)
            throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        var request = StripeGetPaymentIntentRequest.of(charge, stripeGatewayConfig);
        String rawResponse = client.getRequestFor(request).getEntity();
        return jsonObjectMapper.getObject(rawResponse, StripePaymentIntent.class);
    }

    private void transferFeeFromConnectAccount(long feeAmount,
                                               ChargeEntity charge)
            throws GatewayException.GatewayErrorException, GatewayException.GenericGatewayException, GatewayException.GatewayConnectionTimeoutException {
        StripeTransferInRequest transferInRequest = StripeTransferInRequest.createFeesForFailedPaymentTransferRequest(
                String.valueOf(feeAmount),
                charge.getGatewayAccount(),
                charge.getGatewayAccountCredentialsEntity(),
                charge.getGatewayTransactionId(),
                charge.getExternalId(),
                stripeGatewayConfig);
        String rawResponse = client.postRequestFor(transferInRequest).getEntity();
        Transfer stripeTransfer = ApiResource.GSON.fromJson(rawResponse, Transfer.class);

        LOGGER.info("To collect fees for failed payment {}, transferred net amount {} - transfer id {} - from Stripe Connect account id {} in transfer group {}",
                charge.getExternalId(),
                feeAmount,
                stripeTransfer.getId(),
                stripeTransfer.getDestination(),
                stripeTransfer.getTransferGroup()
        );
    }
}
