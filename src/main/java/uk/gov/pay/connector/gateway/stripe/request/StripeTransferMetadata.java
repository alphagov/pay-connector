package uk.gov.pay.connector.gateway.stripe.request;

import java.util.Map;
import java.util.Optional;

public class StripeTransferMetadata {
    public static final String STRIPE_CHARGE_ID_KEY = "stripe_charge_id";
    public static final String GOVUK_PAY_TRANSACTION_EXTERNAL_ID = "govuk_pay_transaction_external_id";
    public static final String REASON_KEY = "reason";

    private final String stripeChargeId;
    private final String govukPayTransactionExternalId;
    private final StripeTransferMetadataReason reason;

    public StripeTransferMetadata(String stripeChargeId, String reconciliationTransactionId, StripeTransferMetadataReason reason) {
        this.stripeChargeId = stripeChargeId;
        this.govukPayTransactionExternalId = reconciliationTransactionId;
        this.reason = reason;
    }

    public static StripeTransferMetadata from(Map<String, String> metadata) {
        return new StripeTransferMetadata(
                metadata.get(STRIPE_CHARGE_ID_KEY),
                metadata.get(GOVUK_PAY_TRANSACTION_EXTERNAL_ID),
                StripeTransferMetadataReason.fromString(metadata.get(REASON_KEY)));
    }

    public Map<String, String> getParams() {
        return Map.of(
                formatMetadataRequestKey(STRIPE_CHARGE_ID_KEY), this.stripeChargeId,
                formatMetadataRequestKey(GOVUK_PAY_TRANSACTION_EXTERNAL_ID), this.govukPayTransactionExternalId,
                formatMetadataRequestKey(REASON_KEY), this.reason.toString()
        );
    }

    private String formatMetadataRequestKey(String key) {
        return String.format("metadata[%s]", key);
    }

    public String getStripeChargeId() {
        return stripeChargeId;
    }

    public String getGovukPayTransactionExternalId() {
        return govukPayTransactionExternalId;
    }

    public StripeTransferMetadataReason getReason() {
        return reason;
    }
}
