package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.TransactionType;

public class TransactionResponse extends ChargeResponse {

    public static class TransactionResponseBuilder extends AbstractChargeResponseBuilder<TransactionResponseBuilder, TransactionResponse> {

        private String transactionType;

        public TransactionResponseBuilder withTransactionType(TransactionType transactionType) {
            this.transactionType = transactionType.toString();
            return this;
        }

        @Override
        protected TransactionResponseBuilder thisObject() {
            return this;
        }

        @Override
        public TransactionResponse build() {
            return new TransactionResponse(this);
        }

    }

    public static TransactionResponseBuilder aTransactionResponseBuilder() {
        return new TransactionResponseBuilder();
    }

    @JsonProperty(value = "transaction_type")
    private String transactionType;

    protected TransactionResponse(TransactionResponseBuilder builder) {
        super(builder);
        this.transactionType = builder.transactionType;
    }


}
