package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;

import java.util.List;
import java.util.Map;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;
        private PersistedCard persistedCard;
        private GatewayAccountEntity gatewayAccount;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            ExternalChargeState externalChargeState = ChargeStatus.fromString(status).toExternal();
            super.withState(new ExternalTransactionState(externalChargeState.getStatus(), externalChargeState.isFinished(), externalChargeState.getCode(), externalChargeState.getMessage()));
            return this;
        }

        public FrontendChargeResponseBuilder withChargeCardDetails(PersistedCard persistedCard) {
            this.persistedCard = persistedCard;
            return this;
        }

        public FrontendChargeResponseBuilder withGatewayAccount(GatewayAccountEntity gatewayAccountEntity) {
            this.gatewayAccount = gatewayAccountEntity;
            return this;
        }

        @Override
        protected FrontendChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public FrontendChargeResponse build() {
            return new FrontendChargeResponse(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl,
                    email, description, reference, providerName, createdDate, links, status, refundSummary,
                    settlementSummary, persistedCard, auth3dsData, gatewayAccount, language, delayedCapture,
                    corporateSurcharge, totalAmount);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    @JsonProperty(value = "gateway_account")
    private GatewayAccountEntity gatewayAccount;

    private FrontendChargeResponse(String chargeId, Long amount, ExternalTransactionState state, String cardBrand,
                                   String gatewayTransactionId, String returnUrl, String email, String description,
                                   ServicePaymentReference reference, String providerName, String createdDate,
                                   List<Map<String, Object>> dataLinks, String status, RefundSummary refundSummary,
                                   SettlementSummary settlementSummary, PersistedCard chargeCardDetails,
                                   Auth3dsData auth3dsData, GatewayAccountEntity gatewayAccount,
                                   SupportedLanguage language, boolean delayedCapture,
                                   Long corporateSurcharge, Long totalAmount) {
        super(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference,
                providerName, createdDate, dataLinks, refundSummary, settlementSummary, chargeCardDetails, auth3dsData,
                language, delayedCapture, corporateSurcharge, totalAmount);
        this.status = status;
        this.gatewayAccount = gatewayAccount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FrontendChargeResponse that = (FrontendChargeResponse) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        if (cardDetails != null ? !cardDetails.equals(that.cardDetails) : that.cardDetails != null) return false;
        return gatewayAccount != null ? gatewayAccount.equals(that.gatewayAccount) : that.gatewayAccount == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (cardDetails != null ? cardDetails.hashCode() : 0);
        result = 31 * result + (gatewayAccount != null ? gatewayAccount.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
