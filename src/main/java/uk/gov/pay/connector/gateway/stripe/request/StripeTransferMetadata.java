package uk.gov.pay.connector.gateway.stripe.request;

import java.util.Map;

public class StripeTransferMetadata {
    public static final String STRIPE_CHARGE_ID_KEY = "stripe_charge_id";
    public static final String RECONCILIATION_TRANSACTION_ID_KEY = "reconciliation_transaction_id";

    private final String stripeChargeId;
    private final String reconciliationTransactionId;

    public StripeTransferMetadata(String stripeChargeId, String reconciliationTransactionId) {
        this.stripeChargeId = stripeChargeId;
        this.reconciliationTransactionId = reconciliationTransactionId;
    }

    public static StripeTransferMetadata from(Map<String, String> metadata) {
        return new StripeTransferMetadata(metadata.get(STRIPE_CHARGE_ID_KEY), metadata.get(RECONCILIATION_TRANSACTION_ID_KEY));
    }

    public Map<String, String> format() {
        return Map.of(
                formatMetadataRequestKey(STRIPE_CHARGE_ID_KEY), this.stripeChargeId,
                formatMetadataRequestKey(RECONCILIATION_TRANSACTION_ID_KEY), this.reconciliationTransactionId
        );
    }
    
    private String formatMetadataRequestKey(String key) {
        return "metadata[" + key + "]";
    }

    public String getStripeChargeId() {
        return stripeChargeId;
    }

    public String getReconciliationTransactionId() {
        return reconciliationTransactionId;
    }
}
