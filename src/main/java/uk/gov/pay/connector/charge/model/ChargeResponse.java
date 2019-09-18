package uk.gov.pay.connector.charge.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import uk.gov.pay.commons.api.json.ApiResponseDateTimeSerializer;
import uk.gov.pay.commons.api.json.ExternalMetadataSerialiser;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.commons.model.charge.ExternalMetadata;
import uk.gov.pay.connector.charge.model.builder.AbstractChargeResponseBuilder;
import uk.gov.pay.connector.charge.model.domain.PersistedCard;
import uk.gov.pay.connector.charge.model.telephone.PaymentOutcome;
import uk.gov.pay.connector.common.model.api.ExternalTransactionState;
import uk.gov.pay.connector.util.DateTimeUtils;
import uk.gov.pay.connector.wallets.WalletType;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static uk.gov.pay.commons.model.ApiResponseDateTimeFormatter.ISO_INSTANT_MILLISECOND_PRECISION;

@JsonInclude(Include.NON_NULL)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public class ChargeResponse {

    @JsonProperty("links")
    private List<Map<String, Object>> dataLinks;

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

    @JsonProperty("telephone_number")
    private String telephoneNumber;

    @JsonProperty
    private String description;

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    private ServicePaymentReference reference;

    @JsonProperty("payment_provider")
    private String providerName;

    @JsonProperty("processor_id")
    private String processorId;

    @JsonProperty("provider_id")
    private String providerId;

    @JsonProperty("created_date")
    @JsonSerialize(using = ApiResponseDateTimeSerializer.class)
    private ZonedDateTime createdDate;

    @JsonProperty("authorised_date")
    @JsonSerialize(using = ApiResponseDateTimeSerializer.class)
    private ZonedDateTime authorisedDate;

    @JsonProperty("payment_outcome")
    private PaymentOutcome paymentOutcome;

    @JsonProperty("refund_summary")
    private RefundSummary refundSummary;

    @JsonProperty("settlement_summary")
    private SettlementSummary settlementSummary;

    @JsonProperty("auth_code")
    private String authCode;

    @JsonProperty("auth_3ds_data")
    private Auth3dsData auth3dsData;

    @JsonProperty("card_details")
    protected PersistedCard cardDetails;

    @JsonProperty
    @JsonSerialize(using = ToStringSerializer.class)
    private SupportedLanguage language;

    @JsonProperty("delayed_capture")
    private boolean delayedCapture;

    @JsonProperty("corporate_card_surcharge")
    private Long corporateCardSurcharge;

    @JsonProperty("fee")
    private Long fee;

    @JsonProperty("total_amount")
    private Long totalAmount;

    @JsonProperty("net_amount")
    private Long netAmount;

    @JsonProperty("wallet_type")
    private WalletType walletType;

    @JsonProperty("metadata")
    @JsonSerialize(using = ExternalMetadataSerialiser.class)
    private ExternalMetadata externalMetadata;

    ChargeResponse(AbstractChargeResponseBuilder<?, ? extends ChargeResponse> builder) {
        this.dataLinks = builder.getLinks();
        this.chargeId = builder.getChargeId();
        this.amount = builder.getAmount();
        this.paymentOutcome = builder.getPaymentOutcome();
        this.authCode = builder.getAuthCode();
        this.state = builder.getState();
        this.cardBrand = builder.getCardBrand();
        this.gatewayTransactionId = builder.getGatewayTransactionId();
        this.returnUrl = builder.getReturnUrl();
        this.description = builder.getDescription();
        this.reference = builder.getReference();
        this.providerName = builder.getProviderName();
        this.providerId = builder.getProviderId();
        this.processorId = builder.getProcessorId();
        this.createdDate = builder.getCreatedDate();
        this.authorisedDate = builder.getAuthorisedDate();
        this.email = builder.getEmail();
        this.telephoneNumber = builder.getTelephoneNumber();
        this.refundSummary = builder.getRefundSummary();
        this.settlementSummary = builder.getSettlementSummary();
        this.cardDetails = builder.getCardDetails();
        this.auth3dsData = builder.getAuth3dsData();
        this.language = builder.getLanguage();
        this.delayedCapture = builder.isDelayedCapture();
        this.corporateCardSurcharge = builder.getCorporateCardSurcharge();
        this.fee = builder.getFee();
        this.totalAmount = builder.getTotalAmount();
        this.netAmount = builder.getNetAmount();
        this.walletType = builder.getWalletType();
        this.externalMetadata = builder.getExternalMetadata();
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

    public ServicePaymentReference getReference() {
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

    public SupportedLanguage getLanguage() {
        return language;
    }

    public boolean getDelayedCapture() {
        return delayedCapture;
    }

    public Long getCorporateCardSurcharge() {
        return corporateCardSurcharge;
    }

    public Long getFee() {
        return fee;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public WalletType getWalletType() {
        return walletType;
    }

    public ExternalMetadata getExternalMetadata() {
        return externalMetadata;
    }

    public String getTelephoneNumber() {
        return telephoneNumber;
    }

    public String getProcessorId() {
        return processorId;
    }

    public String getProviderId() {
        return providerId;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public ZonedDateTime getAuthorisedDate() {
        return authorisedDate;
    }

    public PaymentOutcome getPaymentOutcome() {
        return paymentOutcome;
    }

    public String getAuthCode() {
        return authCode;
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
        return delayedCapture == that.delayedCapture &&
                Objects.equals(dataLinks, that.dataLinks) &&
                Objects.equals(chargeId, that.chargeId) &&
                Objects.equals(amount, that.amount) &&
                Objects.equals(state, that.state) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(gatewayTransactionId, that.gatewayTransactionId) &&
                Objects.equals(returnUrl, that.returnUrl) &&
                Objects.equals(email, that.email) &&
                Objects.equals(description, that.description) &&
                Objects.equals(reference, that.reference) &&
                Objects.equals(providerName, that.providerName) &&
                Objects.equals(createdDate, that.createdDate) &&
                Objects.equals(refundSummary, that.refundSummary) &&
                Objects.equals(settlementSummary, that.settlementSummary) &&
                Objects.equals(auth3dsData, that.auth3dsData) &&
                Objects.equals(cardDetails, that.cardDetails) &&
                language == that.language &&
                Objects.equals(corporateCardSurcharge, that.corporateCardSurcharge) &&
                Objects.equals(totalAmount, that.totalAmount) &&
                walletType == that.walletType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataLinks, chargeId, amount, state, cardBrand, gatewayTransactionId, returnUrl, email,
                description, reference, providerName, createdDate, refundSummary, settlementSummary, auth3dsData,
                cardDetails, language, delayedCapture, corporateCardSurcharge, totalAmount, walletType);
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
                ", language=" + language +
                ", delayedCapture=" + delayedCapture +
                ", corporateCardSurcharge=" + corporateCardSurcharge +
                ", totalAmount=" + totalAmount +
                ", walletType=" + walletType +
                '}';
    }

    public static class ChargeResponseBuilder extends AbstractChargeResponseBuilder<ChargeResponseBuilder, ChargeResponse> {
        @Override
        protected ChargeResponseBuilder thisObject() {
            return this;
        }

        @Override
        public ChargeResponse build() {
            return new ChargeResponse(this);
        }
    }

    public static ChargeResponseBuilder aChargeResponseBuilder() {
        return new ChargeResponseBuilder();
    }


    @JsonFormat(shape = JsonFormat.Shape.OBJECT)
    public static class RefundSummary {

        @JsonProperty("status")
        private String status;
        @JsonProperty("user_external_id")
        private String userExternalId;
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

        public String getUserExternalId() {
            return userExternalId;
        }

        public void setUserExternalId(String userExternalId) {
            this.userExternalId = userExternalId;
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            RefundSummary that = (RefundSummary) o;

            if (!status.equals(that.status)) {
                return false;
            }
            if (userExternalId != null ? !userExternalId.equals(that.userExternalId)
                    : that.userExternalId != null) {
                return false;
            }
            if (amountAvailable != null ? !amountAvailable.equals(that.amountAvailable)
                    : that.amountAvailable != null) {
                return false;
            }
            return amountSubmitted != null ? amountSubmitted.equals(that.amountSubmitted)
                    : that.amountSubmitted == null;
        }

        @Override
        public int hashCode() {
            int result = status.hashCode();
            result = 31 * result + (userExternalId != null ? userExternalId.hashCode() : 0);
            result = 31 * result + (amountAvailable != null ? amountAvailable.hashCode() : 0);
            result = 31 * result + (amountSubmitted != null ? amountSubmitted.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "RefundSummary{" +
                    "status='" + status + '\'' +
                    "userExternalId='" + userExternalId + '\'' +
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
            return (captureSubmitTime != null) ? ISO_INSTANT_MILLISECOND_PRECISION.format(captureSubmitTime) : null;
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

        @JsonProperty("md")
        private String md;

        @JsonProperty("worldpayChallengeJwt")
        private String worldpayChallengeJwt;

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

        public void setMd(String md) {
            this.md = md;
        }

        public String getMd() {
            return md;
        }

        @JsonIgnore
        public Optional<String> getWorldpayChallengeJwt() {
            return Optional.ofNullable(worldpayChallengeJwt);
        }

        public void setWorldpayChallengeJwt(String worldpayChallengeJwt) {
            this.worldpayChallengeJwt = worldpayChallengeJwt;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Auth3dsData that = (Auth3dsData) o;
            return Objects.equals(paRequest, that.paRequest) &&
                    Objects.equals(issuerUrl, that.issuerUrl) &&
                    Objects.equals(htmlOut, that.htmlOut) &&
                    Objects.equals(md, that.md) &&
                    Objects.equals(worldpayChallengeJwt, that.worldpayChallengeJwt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paRequest, issuerUrl, htmlOut, md, worldpayChallengeJwt);
        }

        @Override
        public String toString() {
            return "Auth3dsData{" +
                    "paRequest='" + paRequest + '\'' +
                    ", issuerUrl='" + issuerUrl + '\'' +
                    ", htmlOut='" + htmlOut + '\'' +
                    ", md='" + md + '\'' +
                    ", worldpayChallengeJwt='" + worldpayChallengeJwt + '\'' +
                    '}';
        }
    }
}


