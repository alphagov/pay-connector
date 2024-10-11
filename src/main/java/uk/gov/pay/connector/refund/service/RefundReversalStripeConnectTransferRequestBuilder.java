package uk.gov.pay.connector.refund.service;


import com.google.inject.Inject;
import com.google.inject.Singleton;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;

@Singleton
public class RefundReversalStripeConnectTransferRequestBuilder {

    @Inject
    public RefundReversalStripeConnectTransferRequestBuilder() {
    }

    public Map<String, Object> createRequest(String correctionPaymentId, com.stripe.model.Refund refundFromStripe) {
        String stripeChargeId = refundFromStripe.getChargeObject().getId();
        String destination = refundFromStripe.getChargeObject().getOnBehalfOf();
        String transferGroup = refundFromStripe.getChargeObject().getTransferGroup();
        long amount = refundFromStripe.getAmount();
        String currency = refundFromStripe.getCurrency();

        return Map.of(
                "destination", destination,
                "amount", amount,
                "metadata", Map.of(
                        "stripe_charge_id", stripeChargeId,
                        "govuk_pay_transaction_external_id", correctionPaymentId
                ),
                "currency", currency,
                "transfer_group", transferGroup,
                "expand", List.of("balance_transaction", "destination_payment")
        );

    }
}
