package uk.gov.pay.connector.refund.service;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.HashMap;
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

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("stripeChargeId", stripeChargeId);
        metadata.put("correctionPaymentId", correctionPaymentId);

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("destination", destination);
        requestMap.put("amount", amount);
        requestMap.put("metadata", metadata);
        requestMap.put("currency", currency);
        requestMap.put("transferGroup", transferGroup);
        requestMap.put("expand", new String[]{"balance_transaction", "destination_payment"});

        return requestMap;
    }
}
