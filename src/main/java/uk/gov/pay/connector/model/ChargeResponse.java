package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import uk.gov.pay.connector.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.util.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class ChargeResponse {

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    static public class Refund {
        @JsonProperty("status")
        public String status;

        @JsonProperty("amount_available")
        public   Long amountAvailable;

        @JsonProperty("amount_submitted")
        public   Long amountSubmitted;

        public void setStatus(String status) {
            this.status = status;
        }

        public void setAmountAvailable(Long amountAvailable) {
            this.amountAvailable = amountAvailable;
        }

        public void setAmountSubmitted(Long amountSubmitted) {
            this.amountSubmitted = amountSubmitted;
        }

        public Long getAmountAvailable() {
            return amountAvailable;
        }

        public Long getAmountSubmitted() {
            return amountSubmitted;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class ChargeResponseBuilder extends AbstractChargeResponseBuilder<ChargeResponseBuilder, ChargeResponse> {
        @Override
        protected ChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public ChargeResponse build() {
            return new ChargeResponse(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, refunds);
        }
    }

    public static ChargeResponseBuilder aChargeResponse() {
        return new ChargeResponseBuilder();
    }

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();

    @JsonProperty("charge_id")
    private String chargeId;

    @JsonProperty
    private Long amount;

    @JsonProperty
    private ExternalChargeState state;

    @JsonProperty("gateway_transaction_id")
    private String gatewayTransactionId;

    @JsonProperty("return_url")
    private String returnUrl;

    @JsonProperty("email")
    private String email;

    @JsonProperty
    private String description;

    @JsonProperty
    private String reference;

    @JsonProperty("payment_provider")
    private String providerName;

    private ZonedDateTime createdDate;

    @JsonProperty("created_date")
    public String getCreatedDate() {
        return DateTimeUtils.toUTCDateString(createdDate);
    }

    @JsonProperty("refunds")
    public Refund refunds;

    protected ChargeResponse(String chargeId, Long amount, ExternalChargeState state, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, Refund refund) {
        this.dataLinks = dataLinks;
        this.chargeId = chargeId;
        this.amount = amount;
        this.state = state;
        this.gatewayTransactionId = gatewayTransactionId;
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.providerName = providerName;
        this.createdDate = createdDate;
        this.email = email;
        this.refunds = refund;
    }

    public URI getLink(String rel) {
        return dataLinks.stream()
                .filter(map -> rel.equals(map.get("rel")))
                .findFirst()
                .map(link -> (URI) link.get("href"))
                .get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ChargeResponse) {
            ChargeResponse that = (ChargeResponse)o;
            return new EqualsBuilder()
                    .append(this.dataLinks, that.dataLinks)
                    .append(this.chargeId, that.chargeId)
                    .append(this.amount, that.amount)
                    .append(this.state, that.state)
                    .append(this.gatewayTransactionId, that.gatewayTransactionId)
                    .append(this.returnUrl, that.returnUrl)
                    .append(this.email, that.email)
                    .append(this.description, that.description)
                    .append(this.reference, that.reference)
                    .append(this.providerName, that.providerName)
                    .append(this.createdDate, that.createdDate)
                    .build();
        }

        return false;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).reflectionToString(this);
    }
}


