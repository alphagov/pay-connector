package uk.gov.pay.connector.paritycheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.commons.model.Source;
import uk.gov.pay.commons.model.SupportedLanguage;
import uk.gov.pay.connector.charge.model.ChargeResponse;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class LedgerTransaction {

    private String transactionId;
    private Long amount;
    private Long gatewayAccountId;
    private String description;
    private String reference;
    private String email;
    private boolean delayedCapture;
    private Long corporateCardSurcharge;
    private Long totalAmount;
    private Long fee;
    private Long netAmount;
    private String createdDate;
    private TransactionState state;
    private String returnUrl;
    private String paymentProvider;
    private ChargeResponse.RefundSummary refundSummary;
    private ChargeResponse.SettlementSummary settlementSummary;
    private CardDetails cardDetails;
    private SupportedLanguage language;
    private boolean moto;
    private Boolean live;
    private Source source;
    private String gatewayTransactionId;
    private String walletType;
    private Map<String, Object> externalMetaData;

    public Long getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getReference() {
        return reference;
    }

    public String getEmail() {
        return email;
    }

    public boolean getDelayedCapture() {
        return delayedCapture;
    }

    public Long getCorporateCardSurcharge() {
        return corporateCardSurcharge;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public Long getFee() {
        return fee;
    }

    public Long getNetAmount() {
        return netAmount;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(TransactionState transactionState) {
        this.state = transactionState;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public boolean isDelayedCapture() {
        return delayedCapture;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public ChargeResponse.RefundSummary getRefundSummary() {
        return refundSummary;
    }

    public ChargeResponse.SettlementSummary getSettlementSummary() {
        return settlementSummary;
    }

    public CardDetails getCardDetails() {
        return cardDetails;
    }

    public SupportedLanguage getLanguage() {
        return language;
    }

    public boolean isMoto() {
        return moto;
    }

    public Boolean getLive() {
        return live;
    }

    public Source getSource() {
        return source;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getWalletType() {
        return walletType;
    }

    public Map<String, Object> getExternalMetaData() {
        return externalMetaData;
    }
}
