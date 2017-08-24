package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
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
    private String cardBrand;
    private Set<ChargeStatus> internalChargeStatuses = new HashSet<>();
    private Set<RefundStatus> internalRefundStatuses = new HashSet<>();

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

    public Set<String> getExternalChargeStates() {
        return this.internalChargeStatuses.stream()
                .map(s -> s.toExternal().getStatus())
                .collect(Collectors.toSet());
    }

    public Set<String> getExternalRefundStates() {
        return this.internalRefundStatuses.stream()
                .map(s -> s.toExternal().getStatus())
                .collect(Collectors.toSet());
    }

    public Set<ChargeStatus> getInternalChargeStatuses() {
        return this.internalChargeStatuses;
    }

    public Set<RefundStatus> getInternalRefundStatuses() {
        return this.internalRefundStatuses;
    }

    public ChargeSearchParams withExternalChargeStates(Set<String> state) {
        state.stream().forEach(this::withExternalChargeState);
        return this;
    }

    public ChargeSearchParams withExternalChargeState(String state) {
        if (state != null) {
            this.internalChargeStatuses.addAll(
                    parseChargeState(state).stream()
                            .map(ChargeStatus::fromExternal)
                            .flatMap(l -> l.stream())
                            .collect(Collectors.toSet()));
        }
        return this;
    }

    public ChargeSearchParams withInternalChargeStatuses(List<ChargeStatus> statuses) {
        this.internalChargeStatuses.addAll(statuses);
        return this;
    }

    public ChargeSearchParams withExternalRefundStates(Set<String> state) {
        state.stream().forEach(this::withExternalRefundState);
        return this;
    }

    public ChargeSearchParams withExternalRefundState(String state) {
        if (state != null) {
            this.internalRefundStatuses.addAll(
                    parseRefundState(state).stream()
                            .map(RefundStatus::fromExternal)
                            .flatMap(l -> l.stream())
                            .collect(Collectors.toSet()));
        }
        return this;
    }

    public ChargeSearchParams withInternalRefundStatuses(List<RefundStatus> statuses) {
        this.internalRefundStatuses.addAll(statuses);
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

        getExternalChargeStates().stream()
                .findFirst()
                .ifPresent(state -> builder.append("&charge_state=" + state));

        getExternalRefundStates().stream()
                .findFirst()
                .ifPresent(state -> builder.append("&refund_state=" + state));

        if (isNotBlank(cardBrand)) {
            builder.append("&card_brand=" + cardBrand);
        }
        return builder.toString().replaceFirst("&", "");
    }

    private List<ExternalChargeState> parseChargeState(String state) {
        if (isBlank(state)) {
            new ArrayList<>();
        }
        return ExternalChargeState.fromStatusString(state);
    }

    private List<ExternalRefundStatus> parseRefundState(String state) {
        if (isBlank(state)) {
            new ArrayList<>();
        }
        return ExternalRefundStatus.fromStatusString(state);
    }
}
