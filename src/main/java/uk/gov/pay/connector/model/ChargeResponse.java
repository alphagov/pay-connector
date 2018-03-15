package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.model.api.ExternalTransactionState;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.domain.PersistedCard;
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
    public static class RefundSummary {

        @JsonProperty("status")
        private String status;
        @JsonProperty("amount_available")
        private Long amountAvailable;
        @JsonProperty("amount_submitted")
        private Long amountSubmitted;

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

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class SettlementSummary {
        private ZonedDateTime captureSubmitTime, capturedTime;

        public void setCaptureSubmitTime(ZonedDateTime captureSubmitTime) {
            this.captureSubmitTime = captureSubmitTime;
        }

        @JsonProperty("capture_submit_time")
        public String getCaptureSubmitTime() {
            return (captureSubmitTime != null) ? DateTimeUtils.toUTCDateTimeString(captureSubmitTime) : null;
        }

        public void setCapturedTime(ZonedDateTime capturedTime) {
            this.capturedTime = capturedTime;
        }

        @JsonProperty("captured_date")
        public String getCapturedDate() {
            return (capturedTime != null) ? DateTimeUtils.toUTCDateString(capturedTime) : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SettlementSummary that = (SettlementSummary) o;

            if (captureSubmitTime != null ? !captureSubmitTime.equals(that.captureSubmitTime) : that.captureSubmitTime != null)
                return false;
            return capturedTime != null ? capturedTime.equals(that.capturedTime) : that.capturedTime == null;

        }

        @Override
        public int hashCode() {
            int result = captureSubmitTime != null ? captureSubmitTime.hashCode() : 0;
            result = 31 * result + (capturedTime != null ? capturedTime.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "SettlementSummary{" +
                    ", captureSubmitTime=" + captureSubmitTime +
                    ", capturedTime=" + capturedTime +
                    '}';
        }
    }

    @JsonInclude(Include.NON_NULL)
    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class Auth3dsData {

        @JsonProperty("paRequest")
        private String paRequest;

        @JsonProperty("issuerUrl")
        private String issuerUrl;

        @JsonProperty("htmlOut")
        private String htmlOut;

        public String getPaRequest() {
            return paRequest;
        }

        public void setPaRequest(String paRequest) {
            this.paRequest = paRequest;
        }

        public String getIssuerUrl() {
            return issuerUrl;
        }

        public void setIssuerUrl(String issuerUrl) {
            this.issuerUrl = issuerUrl;
        }

        public String getHtmlOut() {
            return htmlOut;
        }

        public void setHtmlOut(String htmlOut) {
            this.htmlOut = htmlOut;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Auth3dsData that = (Auth3dsData) o;

            if (paRequest != null ? !paRequest.equals(that.paRequest) : that.paRequest != null) return false;
            if (issuerUrl != null ? !issuerUrl.equals(that.issuerUrl) : that.issuerUrl != null) return false;
            return htmlOut != null ? htmlOut.equals(that.htmlOut) : that.htmlOut == null;
        }

        @Override
        public int hashCode() {
            int result = paRequest != null ? paRequest.hashCode() : 0;
            result = 31 * result + (issuerUrl != null ? issuerUrl.hashCode() : 0);
            result = 31 * result + (htmlOut != null ? htmlOut.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Auth3dsData{" +
                    "paRequest='" + paRequest + '\'' +
                    ", issuerUrl='" + issuerUrl + '\'' +
                    ", htmlOut='" + htmlOut + '\'' +
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
            return new ChargeResponse(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email,
                    description, reference, providerName, createdDate, links, refundSummary, settlementSummary, cardDetails, auth3dsData);
        }
    }

    public static ChargeResponseBuilder aChargeResponseBuilder() {
        return new ChargeResponseBuilder();
    }

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();

    @JsonProperty("charge_id")
    private String chargeId;

    @JsonProperty
    private Long amount;

    @JsonProperty
    private ExternalTransactionState state;

    @JsonProperty("card_brand")
    private String cardBrand;

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

    @JsonProperty("created_date")
    private String createdDate;

    @JsonProperty("refund_summary")
    private RefundSummary refundSummary;

    @JsonProperty("settlement_summary")
    private SettlementSummary settlementSummary;

    @JsonProperty("auth_3ds_data")
    private Auth3dsData auth3dsData;

    @JsonProperty("card_details")
    protected PersistedCard cardDetails;

    protected ChargeResponse(String chargeId, Long amount, ExternalTransactionState state, String cardBrand, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, String createdDate, List<Map<String, Object>> dataLinks, RefundSummary refundSummary, SettlementSummary settlementSummary, PersistedCard cardDetails, Auth3dsData auth3dsData) {
        this.dataLinks = dataLinks;
        this.chargeId = chargeId;
        this.amount = amount;
        this.state = state;
        this.cardBrand = cardBrand;
        this.gatewayTransactionId = gatewayTransactionId;
        this.returnUrl = returnUrl;
        this.description = description;
        this.reference = reference;
        this.providerName = providerName;
        this.createdDate = createdDate;
        this.email = email;
        this.refundSummary = refundSummary;
        this.settlementSummary = settlementSummary;
        this.cardDetails = cardDetails;
        this.auth3dsData = auth3dsData;
    }

    public List<Map<String, Object>> getDataLinks() {
        return dataLinks;
    }

    public String getChargeId() {
        return chargeId;
    }

    public Long getAmount() {
        return amount;
    }

    public ExternalTransactionState getState() {
        return state;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getEmail() {
        return email;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getProviderName() {
        return providerName;
    }

    public RefundSummary getRefundSummary() {
        return refundSummary;
    }

    public SettlementSummary getSettlementSummary() {
        return settlementSummary;
    }

    public Auth3dsData getAuth3dsData() {
        return auth3dsData;
    }

    public PersistedCard getCardDetails() {
        return cardDetails;
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

        if (dataLinks != null ? !dataLinks.equals(that.dataLinks) : that.dataLinks != null)
            return false;
        if (chargeId != null ? !chargeId.equals(that.chargeId) : that.chargeId != null)
            return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (state != null ? !state.equals(that.state) : that.state != null) return false;
        if (cardBrand != null ? !cardBrand.equals(that.cardBrand) : that.cardBrand != null)
            return false;
        if (gatewayTransactionId != null ? !gatewayTransactionId.equals(that.gatewayTransactionId) : that.gatewayTransactionId != null)
            return false;
        if (returnUrl != null ? !returnUrl.equals(that.returnUrl) : that.returnUrl != null)
            return false;
        if (email != null ? !email.equals(that.email) : that.email != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null)
            return false;
        if (reference != null ? !reference.equals(that.reference) : that.reference != null)
            return false;
        if (providerName != null ? !providerName.equals(that.providerName) : that.providerName != null)
            return false;
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null)
            return false;
        if (refundSummary != null ? !refundSummary.equals(that.refundSummary) : that.refundSummary != null)
            return false;
        if (settlementSummary != null ? !settlementSummary.equals(that.settlementSummary) : that.settlementSummary != null)
            return false;
        if (auth3dsData != null ? !auth3dsData.equals(that.auth3dsData) : that.auth3dsData != null)
            return false;
        return cardDetails != null ? cardDetails.equals(that.cardDetails) : that.cardDetails == null;

    }

    @Override
    public int hashCode() {
        int result = dataLinks != null ? dataLinks.hashCode() : 0;
        result = 31 * result + (chargeId != null ? chargeId.hashCode() : 0);
        result = 31 * result + (amount != null ? amount.hashCode() : 0);
        result = 31 * result + (state != null ? state.hashCode() : 0);
        result = 31 * result + (cardBrand != null ? cardBrand.hashCode() : 0);
        result = 31 * result + (gatewayTransactionId != null ? gatewayTransactionId.hashCode() : 0);
        result = 31 * result + (returnUrl != null ? returnUrl.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (reference != null ? reference.hashCode() : 0);
        result = 31 * result + (providerName != null ? providerName.hashCode() : 0);
        result = 31 * result + (createdDate != null ? createdDate.hashCode() : 0);
        result = 31 * result + (refundSummary != null ? refundSummary.hashCode() : 0);
        result = 31 * result + (settlementSummary != null ? settlementSummary.hashCode() : 0);
        result = 31 * result + (auth3dsData != null ? auth3dsData.hashCode() : 0);
        result = 31 * result + (cardDetails != null ? cardDetails.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        // Some services put PII in the description, so don’t include it in the stringification
        return "ChargeResponse{" +
                "dataLinks=" + dataLinks +
                ", chargeId='" + chargeId + '\'' +
                ", amount=" + amount +
                ", state=" + state +
                ", cardBrand='" + cardBrand + '\'' +
                ", gatewayTransactionId='" + gatewayTransactionId + '\'' +
                ", returnUrl='" + returnUrl + '\'' +
                ", reference='" + reference + '\'' +
                ", providerName='" + providerName + '\'' +
                ", createdDate=" + createdDate +
                ", refundSummary=" + refundSummary +
                ", settlementSummary=" + settlementSummary +
                ", auth3dsData=" + auth3dsData +
                '}';
    }

}


