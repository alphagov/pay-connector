package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.PersistedCard;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;
        private CardDetailsEntity confirmationDetails;
        private PersistedCard persistedCard;
        private GatewayAccountEntity gatewayAccount;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            super.withState(ChargeStatus.fromString(status).toExternal());
            return this;
        }

        //TODO: leaving for backward compatibility. To remove later
        public FrontendChargeResponseBuilder withConfirmationDetails(CardDetailsEntity cardDetailsEntity) {
            this.confirmationDetails = cardDetailsEntity;
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
            return new FrontendChargeResponse(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, status, refundSummary, confirmationDetails, persistedCard, gatewayAccount);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    //TODO: leaving for backward compatibility
    @JsonProperty(value = "confirmation_details")
    private CardDetailsEntity confirmationDetails;

    @JsonProperty(value = "gateway_account")
    private GatewayAccountEntity gatewayAccount;

    private FrontendChargeResponse(String chargeId, Long amount, ExternalChargeState state, String cardBrand, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, String status, RefundSummary refundSummary, CardDetailsEntity confirmationDetails, PersistedCard chargeCardDetails, GatewayAccountEntity gatewayAccount) {
        super(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, dataLinks, refundSummary, chargeCardDetails);
        this.status = status;
        this.confirmationDetails = confirmationDetails;
        this.gatewayAccount = gatewayAccount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FrontendChargeResponse that = (FrontendChargeResponse) o;

        if (status != null ? !status.equals(that.status) : that.status != null) return false;
        //TODO: leaving for backward compatibility
        if (confirmationDetails != null ? !confirmationDetails.equals(that.confirmationDetails) : that.confirmationDetails != null)
            return false;
        if (cardDetails != null ? !cardDetails.equals(that.cardDetails) : that.cardDetails != null)
            return false;
        return gatewayAccount != null ? gatewayAccount.equals(that.gatewayAccount) : that.gatewayAccount == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        //TODO: leaving for backward compatibility
        result = 31 * result + (confirmationDetails != null ? confirmationDetails.hashCode() : 0);
        result = 31 * result + (cardDetails != null ? cardDetails.hashCode() : 0);
        result = 31 * result + (gatewayAccount != null ? gatewayAccount.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
