package uk.gov.pay.connector.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.model.domain.PersistedCard;
import uk.gov.pay.connector.util.DateTimeUtils;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class TransactionResponse {

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

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class Auth3dsData {

        @JsonProperty("paRequest")
        private String paRequest;

        @JsonProperty("issuerUrl")
        private String issuerUrl;

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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Auth3dsData that = (Auth3dsData) o;

            if (paRequest != null ? !paRequest.equals(that.paRequest) : that.paRequest != null) return false;
            return issuerUrl != null ? issuerUrl.equals(that.issuerUrl) : that.issuerUrl == null;

        }

        @Override
        public int hashCode() {
            int result = paRequest != null ? paRequest.hashCode() : 0;
            result = 31 * result + (issuerUrl != null ? issuerUrl.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Auth3dsData{" +
                    "paRequest='" + paRequest + '\'' +
                    ", issuerUrl='" + issuerUrl + '\'' +
                    '}';
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class ExternalStateDTO{

        @JsonProperty("status")
        private String value;
        @JsonProperty("finished")
        private boolean finished;


        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public boolean isFinished() {
            return finished;
        }

        public void setFinished(boolean finished) {
            this.finished = finished;
        }

        public static ExternalStateDTO fromExternalChargeState(ExternalChargeState state){
            ExternalStateDTO dto = new ExternalStateDTO();
            dto.setFinished(state.isFinished());
            dto.setValue(state.getStatus());
            return dto;
        }

        public static ExternalStateDTO fromExternalRefundState(ExternalRefundStatus state){
            ExternalStateDTO dto = new ExternalStateDTO();
            dto.setValue(state.getStatus());
            return dto;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExternalStateDTO that = (ExternalStateDTO) o;

            if (finished != that.finished) return false;
            return value != null ? value.equals(that.value) : that.value == null;
        }

        @Override
        public int hashCode() {
            int result = value != null ? value.hashCode() : 0;
            result = 31 * result + (finished ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "ExternalStateDTO{" +
                    "value='" + value + '\'' +
                    ", finished=" + finished +
                    '}';
        }
    }

    public static class TransactionResponseBuilder{

        protected String chargeId;
        protected Long amount;
        protected ExternalStateDTO state;
        protected String cardBrand;
        protected String gatewayTransactionId;
        protected String returnUrl;
        protected String description;
        protected ZonedDateTime createdDate;
        protected List<Map<String, Object>> links = new ArrayList<>();
        protected String reference;
        protected String providerName;
        protected String email;
        protected RefundSummary refundSummary;
        protected SettlementSummary settlementSummary;
        protected PersistedCard cardDetails;
        protected Auth3dsData auth3dsData;
        protected String transactionType;

        protected TransactionResponseBuilder thisObject() {
            return this;
        }

        public TransactionResponse build() {
            return new TransactionResponse(chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email,
                    description, reference, providerName, createdDate, links, refundSummary, settlementSummary, cardDetails, auth3dsData, transactionType);
        }

        public TransactionResponseBuilder withChargeId(String chargeId) {
            this.chargeId = chargeId;
            return thisObject();
        }

        public TransactionResponseBuilder withAmount(Long amount) {
            this.amount = amount;
            return thisObject();
        }

        public TransactionResponseBuilder withState(ExternalStateDTO state) {
            this.state = state;
            return thisObject();
        }

        public TransactionResponseBuilder withCardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return thisObject();
        }

        public TransactionResponseBuilder withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return thisObject();
        }

        public TransactionResponseBuilder withReturnUrl(String returnUrl) {
            this.returnUrl = returnUrl;
            return thisObject();
        }

        public TransactionResponseBuilder withEmail(String email) {
            this.email = email;
            return thisObject();
        }

        public TransactionResponseBuilder withDescription(String description) {
            this.description = description;
            return thisObject();
        }

        public TransactionResponseBuilder withReference(String reference) {
            this.reference = reference;
            return thisObject();
        }

        public TransactionResponseBuilder withCreatedDate(ZonedDateTime createdDate) {
            this.createdDate = createdDate;
            return thisObject();
        }

        public TransactionResponseBuilder withLink(String rel, String method, URI href) {
            links.add(ImmutableMap.of(
                    "rel", rel,
                    "method", method,
                    "href", href
            ));
            return thisObject();
        }

        public TransactionResponseBuilder withLink(String rel, String method, URI href, String type, Map<String,Object> params) {
            links.add(ImmutableMap.of(
                    "rel", rel,
                    "method", method,
                    "href", href,
                    "type", type,
                    "params", params
            ));

            return thisObject();
        }

        public TransactionResponseBuilder withProviderName(String providerName) {
            this.providerName = providerName;
            return thisObject();
        }

        public TransactionResponseBuilder withRefunds(RefundSummary refundSummary) {
            this.refundSummary = refundSummary;
            return thisObject();
        }

        public TransactionResponseBuilder withSettlement(SettlementSummary settlementSummary) {
            this.settlementSummary = settlementSummary;
            return thisObject();
        }

        public TransactionResponseBuilder withCardDetails(PersistedCard cardDetails){
            this.cardDetails = cardDetails;
            return thisObject();
        }

        public TransactionResponseBuilder withAuth3dsData(Auth3dsData auth3dsData) {
            this.auth3dsData = auth3dsData;
            return thisObject();
        }

        public TransactionResponseBuilder withTransactionType(String transactionType) {
            this.transactionType = transactionType;
            return thisObject();
        }

    }

    public static TransactionResponseBuilder aTransactionResponse() {
        return new TransactionResponseBuilder();
    }

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks = new ArrayList<>();

    @JsonProperty("charge_id")
    private String chargeId;

    @JsonProperty
    private Long amount;

    @JsonProperty
    private ExternalStateDTO state;

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

    private ZonedDateTime createdDate;

    @JsonProperty("refund_summary")
    private RefundSummary refundSummary;

    @JsonProperty("settlement_summary")
    private SettlementSummary settlementSummary;

    @JsonProperty("auth_3ds_data")
    private Auth3dsData auth3dsData;

    @JsonProperty("card_details")
    protected PersistedCard cardDetails;

    @JsonProperty("created_date")
    public String getCreatedDate() {
        return DateTimeUtils.toUTCDateTimeString(createdDate);
    }

    @JsonProperty("transaction_type")
    private String transactionType;

    protected TransactionResponse(String chargeId, Long amount, ExternalStateDTO state, String cardBrand, String gatewayTransactionId, String returnUrl, String email, String description, String reference, String providerName, ZonedDateTime createdDate, List<Map<String, Object>> dataLinks, RefundSummary refundSummary, SettlementSummary settlementSummary, PersistedCard cardDetails, Auth3dsData auth3dsData,String transactionType) {
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
        this.transactionType = transactionType;
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

        TransactionResponse that = (TransactionResponse) o;

        if (dataLinks != null ? !dataLinks.equals(that.dataLinks) : that.dataLinks != null) return false;
        if (chargeId != null ? !chargeId.equals(that.chargeId) : that.chargeId != null) return false;
        if (amount != null ? !amount.equals(that.amount) : that.amount != null) return false;
        if (state != that.state) return false;
        if (cardBrand != null ? !cardBrand.equals(that.cardBrand) : that.cardBrand != null) return false;
        if (gatewayTransactionId != null ? !gatewayTransactionId.equals(that.gatewayTransactionId) : that.gatewayTransactionId != null)
            return false;
        if (returnUrl != null ? !returnUrl.equals(that.returnUrl) : that.returnUrl != null) return false;
        if (email != null ? !email.equals(that.email) : that.email != null) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (reference != null ? !reference.equals(that.reference) : that.reference != null) return false;
        if (providerName != null ? !providerName.equals(that.providerName) : that.providerName != null) return false;
        if (createdDate != null ? !createdDate.equals(that.createdDate) : that.createdDate != null) return false;
        if (refundSummary != null ? !refundSummary.equals(that.refundSummary) : that.refundSummary != null)
            return false;
        if (settlementSummary != null ? !settlementSummary.equals(that.settlementSummary) : that.settlementSummary != null)
            return false;
        if (auth3dsData != null ? !auth3dsData.equals(that.auth3dsData) : that.auth3dsData != null) return false;
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
        return "TransactionResponse{" +
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


