package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.domain.ChargeCardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;
        private ChargeCardDetailsEntity confirmationDetails;
        private GatewayAccountEntity gatewayAccount;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            super.withState(ChargeStatus.fromString(status).toExternal());
            return this;
        }

        public FrontendChargeResponseBuilder withConfirmationDetails(ChargeCardDetailsEntity chargeCardDetailsEntity) {
            this.confirmationDetails = chargeCardDetailsEntity;
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
            return new FrontendChargeResponse(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, status, refundSummary, confirmationDetails, gatewayAccount);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    @JsonProperty(value="confirmation_details")
    private ChargeCardDetailsEntity confirmationDetails;

    @JsonProperty(value="gateway_account")
    private GatewayAccountEntity gatewayAccount;

    private FrontendChargeResponse(String chargeId, Long amount, ExternalChargeState state, String cardBrand, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, String status, RefundSummary refundSummary, ChargeCardDetailsEntity confirmationDetails, GatewayAccountEntity gatewayAccount) {
        super(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, dataLinks, refundSummary);
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

        if (!status.equals(that.status)) return false;
        if (confirmationDetails != null ? !confirmationDetails.equals(that.confirmationDetails) : that.confirmationDetails != null)
            return false;
        return gatewayAccount.equals(that.gatewayAccount);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + (confirmationDetails != null ? confirmationDetails.hashCode() : 0);
        result = 31 * result + gatewayAccount.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
