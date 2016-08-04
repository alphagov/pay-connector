package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
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
    static public class RefundSummary {
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RefundSummary that = (RefundSummary) o;

            if (!status.equals(that.status)) return false;
            if (!amountAvailable.equals(that.amountAvailable)) return false;
            return amountSubmitted.equals(that.amountSubmitted);

        }

        @Override
        public int hashCode() {
            int result = status.hashCode();
            result = 31 * result + amountAvailable.hashCode();
            result = 31 * result + amountSubmitted.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "RefundSummary{" +
                    "status='" + status + '\'' +
                    ", amountAvailable=" + amountAvailable +
                    ", amountSubmitted=" + amountSubmitted +
                    '}';
        }
    }

    public static class ChargeResponseBuilder extends AbstractChargeResponseBuilder<ChargeResponseBuilder, ChargeResponse> {
        @Override
        protected ChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public ChargeResponse build() {
            return new ChargeResponse(chargeId, amount, state, gatewayTransactionId, returnUrl, email, description, reference, providerName, createdDate, links, refundSummary);
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

    @JsonProperty("refund_summary")
    public RefundSummary refundSummary;

    protected ChargeResponse(String chargeId, Long amount, ExternalChargeState state, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, RefundSummary refundSummary) {
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
        this.refundSummary = refundSummary;
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
        if (o == null || getClass() != o.getClass()) return false;

        ChargeResponse that = (ChargeResponse) o;

        if (dataLinks != null ? !dataLinks.equals(that.dataLinks) : that.dataLinks != null) return false;
        if (chargeId != null ? !chargeId.equals(that.chargeId) : that.chargeId != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (state != that.state) return false;
        if (gatewayTransactionId != null ? !gatewayTransactionId.equals(that.gatewayTransactionId) : that.gatewayTransactionId != null)
            return false;
        if (returnUrl != null ? !returnUrl.equals(that.returnUrl) : that.returnUrl != null) return false;
        if (email != null ? !email.equals(that.email) : that.email != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (reference != null ? !reference.equals(that.reference) : that.reference != null) return false;
        if (providerName != null ? !providerName.equals(that.providerName) : that.providerName != null) return false;
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) return false;
        return !(refundSummary != null ? !refundSummary.equals(that.refundSummary) : that.refundSummary != null);

    }

    @Override
    public int hashCode() {
        int result = dataLinks != null ? dataLinks.hashCode() : 0;
        result = 31 * result + (chargeId != null ? chargeId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (gatewayTransactionId != null ? gatewayTransactionId.hashCode() : 0);
        result = 31 * result + (returnUrl != null ? returnUrl.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (reference != null ? reference.hashCode() : 0);
        result = 31 * result + (providerName != null ? providerName.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (refundSummary != null ? refundSummary.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChargeResponse{" +
                "dataLinks=" + dataLinks +
                ", chargeId='" + chargeId + '\'' +
                ", amount=" + amount +
                ", state=" + state +
                ", gatewayTransactionId='" + gatewayTransactionId + '\'' +
                ", returnUrl='" + returnUrl + '\'' +
                ", email='" + email + '\'' +
                ", description='" + description + '\'' +
                ", reference='" + reference + '\'' +
                ", providerName='" + providerName + '\'' +
                ", createdDate=" + createdDate +
                ", refundSummary=" + refundSummary +
                '}';
    }
}


