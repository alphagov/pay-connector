package uk.gov.pay.connector.gateway.stripe.request;

import java.util.Map;

public class StripeTransferMetadata {
    public static final String STRIPE_CHARGE_ID_KEY = "stripe_charge_id";
    public static final String GOVUK_PAY_TRANSACTION_EXTERNAL_ID = "govuk_pay_transaction_external_id";

    private final String stripeChargeId;
    private final String govukPayTransactionExternalId;

    public StripeTransferMetadata(String stripeChargeId, String reconciliationTransactionId) {
        this.stripeChargeId = stripeChargeId;
        this.govukPayTransactionExternalId = reconciliationTransactionId;
    }

    public static StripeTransferMetadata from(Map<String, String> metadata) {
        return new StripeTransferMetadata(metadata.get(STRIPE_CHARGE_ID_KEY), metadata.get(GOVUK_PAY_TRANSACTION_EXTERNAL_ID));
    }

    public Map<String, String> format() {
        return Map.of(
                formatMetadataRequestKey(STRIPE_CHARGE_ID_KEY), this.stripeChargeId,
                formatMetadataRequestKey(GOVUK_PAY_TRANSACTION_EXTERNAL_ID), this.govukPayTransactionExternalId
        );
    }
    
    private String formatMetadataRequestKey(String key) {
        return "metadata[" + key + "]";
    }

    public String getStripeChargeId() {
        return stripeChargeId;
    }

    public String getGovukPayTransactionExternalId() {
        return govukPayTransactionExternalId;
    }
}
