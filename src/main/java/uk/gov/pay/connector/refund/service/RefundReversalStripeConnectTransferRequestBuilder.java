package uk.gov.pay.connector.refund.service;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

@Singleton
public class RefundReversalStripeConnectTransferRequestBuilder {
    private final RandomIdGenerator randomIdGenerator;

    @Inject
    public RefundReversalStripeConnectTransferRequestBuilder(RandomIdGenerator randomIdGenerator) {
        this.randomIdGenerator = randomIdGenerator;
    }

    public Map<String, Object> createRequest(com.stripe.model.Refund refundFromStripe) {
        String stripeChargeId = refundFromStripe.getChargeObject().getId();
        String destination = refundFromStripe.getChargeObject().getOnBehalfOf();
        String transferGroup = refundFromStripe.getChargeObject().getTransferGroup();
        long amount = refundFromStripe.getAmount();
        String currency = refundFromStripe.getCurrency();
        String correctionPaymentId = randomIdGenerator.random13ByteHexGenerator();


        return Map.of(
                "destination", destination,
                "amount", amount,
                "metadata", Map.of(
                        "stripe_charge_id", stripeChargeId,
                        "correction_payment_id", correctionPaymentId
                ),
                "currency", currency,
                "transfer_group", transferGroup,
                "expand", List.of("balance_transaction", "destination_payment")
        );

    }
}
