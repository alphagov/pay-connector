package uk.gov.pay.connector.refund.service;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import uk.gov.pay.connector.util.RandomIdGenerator;

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
        String destination = refundFromStripe.getChargeObject().getOnBehalfOfObject().getId();
        String transferGroup = refundFromStripe.getChargeObject().getTransferGroup();
        long amount = refundFromStripe.getAmount();
        String currency = refundFromStripe.getCurrency();
        String correctionPaymentId = randomIdGenerator.random13ByteHexGenerator();
        

        return Map.of(
                "destination", destination,
                "amount", amount,
                "metadata", Map.of(
                        "stripeChargeId", stripeChargeId,
                        "correctionPaymentId", correctionPaymentId
                ),
                "currency", currency,
                "transferGroup", transferGroup,
                "expand", new String[]{"balance_transaction", "destination_payment"}
        );

    }
}
