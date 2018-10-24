package uk.gov.pay.connector.charge.dao;

import uk.gov.pay.connector.charge.model.CardHolderName;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.ServicePaymentReference;
import uk.gov.pay.connector.charge.model.TransactionType;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.common.model.api.ExternalChargeState;
import uk.gov.pay.connector.common.model.api.ExternalRefundStatus;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class SearchParams {

    private TransactionType transactionType;
    private LastDigitsCardNumber lastDigitsCardNumber;
    private FirstDigitsCardNumber firstDigitsCardNumber;
    private CardHolderName cardHolderName;
    private Long gatewayAccountId;
    private ServicePaymentReference reference;
    private String email;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private Long page;
    private Long displaySize;
    private List<String> cardBrands = new ArrayList<>();
    private Set<ChargeStatus> internalStates = new HashSet<>();
    private Set<String> externalChargeStates = new HashSet<>();
    private Set<String> externalRefundStates = new HashSet<>();
    private Set<ChargeStatus> internalChargeStatuses = new HashSet<>();
    private Set<RefundStatus> internalRefundStatuses = new HashSet<>();

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public SearchParams withTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public SearchParams withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public SearchParams withEmailLike(String email) {
        this.email = email;
        return this;
    }

    public SearchParams withLastDigitsCardNumber(LastDigitsCardNumber lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        return this;
    }

    public SearchParams withFirstDigitsCardNumber(FirstDigitsCardNumber withFirstDigitsCardNumber) {
        this.firstDigitsCardNumber = withFirstDigitsCardNumber;
        return this;
    }

    public SearchParams withCardHolderNameLike(CardHolderName cardHolderName) {
        this.cardHolderName = cardHolderName;
        return this;
    }

    public Set<String> getExternalChargeStates() {
        return this.internalChargeStatuses.stream()
                .map(s -> s.toExternal().getStatus())
                .sorted()
                .collect(Collectors.toSet());
    }

    public Set<String> getExternalStates() {
        return this.internalStates.stream()
                .map(s -> s.toExternal().getStatus())
                .collect(Collectors.toSet());
    }

    public Set<String> getExternalRefundStates() {
        return this.internalRefundStatuses.stream()
                .map(s -> s.toExternal().getStatus())
                .sorted()
                .collect(Collectors.toSet());
    }

    public LastDigitsCardNumber getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public FirstDigitsCardNumber getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
    }

    public CardHolderName getCardHolderName() {
        return cardHolderName;
    }

    public Set<ChargeStatus> getInternalChargeStatuses() {
        return this.internalChargeStatuses;
    }

    public Set<ChargeStatus> getInternalStates() {
        return this.internalStates;
    }

    public Set<RefundStatus> getInternalRefundStatuses() {
        return this.internalRefundStatuses;
    }

    public SearchParams withExternalState(String state) {
        if (state != null) {
            this.internalStates.addAll(
                    parseChargeState(state).stream()
                            .map(ChargeStatus::fromExternal)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet()));
        }
        return this;
    }

    public SearchParams addExternalChargeStates(List<String> states) {
        if (states != null) {
            this.externalChargeStates.addAll(states);
            this.internalChargeStatuses.addAll(states.stream()
                    .map(this::parseChargeState)
                    .map(externalChargeStates -> externalChargeStates.stream().map(ChargeStatus::fromExternal)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet()))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
        }
        return this;
    }

    public SearchParams addExternalChargeStatesV2(List<String> states) {
        if (states != null) {
            this.externalChargeStates.addAll(states);
            this.internalChargeStatuses.addAll(states.stream()
                    .map(this::parseChargeStateV2)
                    .map(externalChargeStates -> externalChargeStates.stream().map(ChargeStatus::fromExternal)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet()))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet()));
        }
        return this;
    }

    public SearchParams addExternalRefundStates(List<String> states) {
        if (states != null) {
            this.externalRefundStates.addAll(states);
            this.internalRefundStatuses.addAll(states.stream()
                    .map(ExternalRefundStatus::fromPublicStatusLabel)
                    .flatMap(externalRefundStatus -> RefundStatus.fromExternal(externalRefundStatus).stream())
                    .collect(Collectors.toSet()));
        }
        return this;
    }

    public SearchParams withInternalStates(List<ChargeStatus> statuses) {
        this.internalStates.addAll(statuses);
        return this;
    }

    public SearchParams withCardBrand(String cardBrand) {
        this.cardBrands = Collections.singletonList(cardBrand);
        return this;
    }

    public SearchParams withCardBrands(List<String> cardBrands) {
        this.cardBrands = cardBrands;
        return this;
    }

    public ServicePaymentReference getReference() {
        return reference;
    }

    public String getEmail() {
        return email;
    }

    public SearchParams withReferenceLike(ServicePaymentReference reference) {
        this.reference = reference;
        return this;
    }

    public ZonedDateTime getFromDate() {
        return fromDate;
    }

    public SearchParams withFromDate(ZonedDateTime fromDate) {
        this.fromDate = fromDate;
        return this;
    }

    public ZonedDateTime getToDate() {
        return toDate;
    }

    public SearchParams withToDate(ZonedDateTime toDate) {
        this.toDate = toDate;
        return this;
    }

    public Long getPage() {
        return page;
    }

    public SearchParams withPage(Long page) {
        this.page = page;
        return this;
    }

    public Long getDisplaySize() {
        return displaySize;
    }

    public SearchParams withDisplaySize(Long displaySize) {
        if (displaySize <= 0) {
            throw new IllegalArgumentException("displaySize must be a positive integer");
        }

        this.displaySize = displaySize;
        return this;
    }

    public List<String> getCardBrands() {
        return cardBrands;
    }

    public String buildQueryParams() {
        return this.buildQueryParams(false);
    }

    public String buildQueryParamsWithPiiRedaction() {
        return this.buildQueryParams(true);
    }

    private String buildQueryParams(boolean redactPii) {
        StringBuilder builder = new StringBuilder();
        if (transactionType != null) {
            builder.append("&transaction_type=").append(transactionType.getValue());
        }
        if (reference != null && isNotBlank(reference.toString()))
            builder.append("&reference=").append(reference);
        if (email != null) {
            if (redactPii) {
                builder.append("&email=*****");
            } else {
                builder.append("&email=").append(email);
            }
        }
        if (fromDate != null)
            builder.append("&from_date=").append(fromDate);
        if (toDate != null)
            builder.append("&to_date=").append(toDate);
        if (page != null)
            builder.append("&page=").append(page);
        if (displaySize != null)
            builder.append("&display_size=").append(displaySize);
        getExternalStates().stream()
                .findFirst()
                .ifPresent(state -> builder.append("&state=").append(state));

        String paymentStates = this.externalChargeStates.stream()
                .collect(Collectors.joining(","));

        String refundStates = this.externalRefundStates.stream()
                .collect(Collectors.joining(","));

        if (isNotEmpty(paymentStates)) {
            builder.append("&payment_states=").append(paymentStates);
        }

        if (isNotEmpty(refundStates)) {
            builder.append("&refund_states=").append(refundStates);
        }
        if (firstDigitsCardNumber != null)
            builder.append("&first_digits_card_number=").append(firstDigitsCardNumber);
        if (lastDigitsCardNumber != null)
            builder.append("&last_digits_card_number=").append(lastDigitsCardNumber);
        if (cardHolderName != null)
            builder.append("&cardholder_name=").append(cardHolderName);
        if (!cardBrands.isEmpty()) {
            cardBrands.forEach(cardBrand -> builder.append("&card_brand=").append(cardBrand));
        }
        return builder.toString().replaceFirst("&", "");
    }

    private List<ExternalChargeState> parseChargeState(String state) {
        if (isBlank(state)) {
            return new ArrayList<>();
        }
        return ExternalChargeState.fromStatusString(state);
    }

    private List<ExternalChargeState> parseChargeStateV2(String state) {
        if (isBlank(state)) {
            return new ArrayList<>();
        }
        return ExternalChargeState.fromStatusStringV2(state);
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }
}
