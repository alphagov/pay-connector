package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeSearchParams {

    private String transactionType;
    private Long gatewayAccountId;
    private String reference;
    private String email;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private Long page;
    private Long displaySize;
    private String externalChargeState;
    private String externalRefundStatus;
    private RefundStatus refundStatus;
    private String cardBrand;
    private Set<ChargeStatus> chargeStatuses = new HashSet<>();

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public ChargeSearchParams withTransactionType(String transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public ChargeSearchParams withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public ChargeSearchParams withEmailLike(String email) {
        this.email = email;
        return this;
    }

    public Set<ChargeStatus> getChargeStatuses() {
        return chargeStatuses;
    }


    public ChargeSearchParams withExternalChargeState(String state) {
        if (state != null) {
            this.externalChargeState = state;
            for (ExternalChargeState externalState : parseChargeState(state)) {
                this.chargeStatuses.addAll(ChargeStatus.fromExternal(externalState));
            }
        }
        return this;
    }

    public ChargeSearchParams withExternalRefundState(String state) {
        if (state != null) {
            this.externalRefundStatus = state;
            this.refundStatus = RefundStatus.fromString(state);
        }
        return this;
    }

    public ChargeSearchParams withCardBrand(String cardBrand) {
        this.cardBrand = cardBrand;
        return this;
    }

    public String getReference() {
        return reference;
    }

    public String getEmail() {
        return email;
    }

    public ChargeSearchParams withReferenceLike(String reference) {
        this.reference = reference;
        return this;
    }

    public ZonedDateTime getFromDate() {
        return fromDate;
    }

    public ChargeSearchParams withFromDate(ZonedDateTime fromDate) {
        this.fromDate = fromDate;
        return this;
    }

    public ZonedDateTime getToDate() {
        return toDate;
    }

    public ChargeSearchParams withToDate(ZonedDateTime toDate) {
        this.toDate = toDate;
        return this;
    }

    public Long getPage() {
        return page;
    }

    public ChargeSearchParams withPage(Long page) {
        this.page = page;
        return this;
    }

    public Long getDisplaySize() {
        return displaySize;
    }

    public ChargeSearchParams withDisplaySize(Long displaySize) {
        if (displaySize <= 0) {
            throw new IllegalArgumentException("displaySize must be a positive integer");
        }

        this.displaySize = displaySize;
        return this;
    }

    public ChargeSearchParams withInternalChargeStatuses(List<ChargeStatus> statuses) {
        this.chargeStatuses = new HashSet<>(statuses);
        return this;
    }

    public String getCardBrand() {
        return cardBrand;
    }

    public String buildQueryParams() {
        StringBuilder builder = new StringBuilder();
        if (isNotBlank(transactionType)) {
            builder.append("&transaction_type=" + transactionType);
        }
        if (isNotBlank(reference))
            builder.append("&reference=" + reference);
        if (email != null)
            builder.append("&email=" + email);
        if (fromDate != null)
            builder.append("&from_date=" + fromDate);
        if (toDate != null)
            builder.append("&to_date=" + toDate);
        if (page != null)
            builder.append("&page=" + page);
        if (displaySize != null)
            builder.append("&display_size=" + displaySize);
        if (isNotBlank(externalChargeState)) {
            builder.append("&charge_state=" + externalChargeState);
        }
        if (isNotBlank(externalRefundStatus)) {
            builder.append("&refund_status=" + externalRefundStatus);
        }
        if (isNotBlank(cardBrand)) {
            builder.append("&card_brand=" + cardBrand);
        }
        return builder.toString().replaceFirst("&", "");
    }

    private List<ExternalChargeState> parseChargeState(String state) {
        List<ExternalChargeState> externalStates = new ArrayList<>();
        if (isNotBlank(state)) {
            externalStates.addAll(ExternalChargeState.fromStatusString(state));
        }
        return externalStates;
    }
}
