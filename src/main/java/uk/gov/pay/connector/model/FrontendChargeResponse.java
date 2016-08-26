package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.ConfirmationDetailsEntity;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;
        private ConfirmationDetailsEntity confirmationDetails;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            super.withState(ChargeStatus.fromString(status).toExternal());
            return this;
        }

        public FrontendChargeResponseBuilder withConfirmationDetails(ConfirmationDetailsEntity confirmationDetailsEntity) {
            this.confirmationDetails = confirmationDetailsEntity;
            return this;
        }

        @Override
        protected FrontendChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public FrontendChargeResponse build() {
            return new FrontendChargeResponse(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, status, refundSummary, confirmationDetails);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    @JsonProperty(value="confirmation_details")
    private ConfirmationDetailsEntity confirmationDetails;

    private FrontendChargeResponse(String chargeId, Long amount, ExternalChargeState state, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, String status, RefundSummary refundSummary, ConfirmationDetailsEntity confirmationDetails) {
        super(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, dataLinks, refundSummary);
        this.status = status;
        this.confirmationDetails = confirmationDetails;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        FrontendChargeResponse that = (FrontendChargeResponse) o;

        if (!status.equals(that.status)) return false;
        return confirmationDetails != null ? confirmationDetails.equals(that.confirmationDetails) : that.confirmationDetails == null;

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + (confirmationDetails != null ? confirmationDetails.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
