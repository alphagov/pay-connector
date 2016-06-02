package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.AbstractChargeResponseBuilder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class FrontendChargeResponse extends ChargeResponse {
    public static class FrontendChargeResponseBuilder extends AbstractChargeResponseBuilder<FrontendChargeResponseBuilder, FrontendChargeResponse> {
        private String status;

        public FrontendChargeResponseBuilder withStatus(String status) {
            this.status = status;
            super.withState(ChargeStatus.fromString(status).toExternal());
            return this;
        }

        @Override
        protected FrontendChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public FrontendChargeResponse build() {
            return new FrontendChargeResponse(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, status);
        }
    }

    public static FrontendChargeResponseBuilder aFrontendChargeResponse() {
        return new FrontendChargeResponseBuilder();
    }

    @JsonProperty
    private String status;

    private FrontendChargeResponse(String chargeId, Long amount, ExternalChargeState state, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, String status) {
        super(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, dataLinks);
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof FrontendChargeResponse) {
            FrontendChargeResponse that = (FrontendChargeResponse)o;
            return new EqualsBuilder()
                .appendSuper(super.equals(that))
                .append(this.status, that.status)
                .isEquals();
        }

        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode()).append(this.status).build();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).reflectionToString(this);
    }
}
