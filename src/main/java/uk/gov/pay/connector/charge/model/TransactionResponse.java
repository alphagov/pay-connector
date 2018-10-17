package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;

import java.util.List;
import java.util.Map;

public class TransactionResponse extends ChargeResponse {

    public static class TransactionResponseBuilder extends AbstractChargeResponseBuilder<TransactionResponseBuilder, TransactionResponse> {

        private String transactionType;

        public TransactionResponseBuilder withTransactionType(String transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        @Override
        protected TransactionResponseBuilder thisObject() {
            return this;
        }

        @Override
        public TransactionResponse build() {
            return new TransactionResponse(transactionType, chargeId, amount, state, cardBrand, gatewayTransactionId,
                    returnUrl, email, description, reference, providerName, createdDate, links, refundSummary,
                    settlementSummary, cardDetails, auth3dsData, language, delayedCapture, corporateCardSurcharge, totalAmount);
        }

    }

    public static TransactionResponseBuilder aTransactionResponseBuilder() {
        return new TransactionResponseBuilder();
    }

    @JsonProperty(value = "transaction_type")
    private String transactionType;

    protected TransactionResponse(String transactionType, String chargeId, Long amount, ExternalTransactionState state,
                                  String cardBrand, String gatewayTransactionId, String returnUrl, String email,
                                  String description, ServicePaymentReference reference, String providerName,
                                  String createdDate, List<Map<String, Object>> dataLinks, RefundSummary refundSummary,
                                  SettlementSummary settlementSummary, PersistedCard cardDetails,
                                  Auth3dsData auth3dsData, SupportedLanguage language,
                                  boolean delayedCapture, Long corporateCardSurcharge, Long totalAmount) {
        super(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference,
                providerName, createdDate, dataLinks, refundSummary, settlementSummary, cardDetails, auth3dsData, 
                language, delayedCapture, corporateCardSurcharge, totalAmount);
        this.transactionType = transactionType;
    }


}
