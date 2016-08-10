package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ChargeSearchParams {

    private Long gatewayAccountId;
    private String reference;
    private String email;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private Long page;
    private Long displaySize;
    private Set<ChargeStatus> chargeStatuses = new HashSet<>();
    private String externalChargeState;

    public Long getGatewayAccountId() {
        return gatewayAccountId;
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
            for (ExternalChargeState externalState : parseState(state)) {
                this.chargeStatuses.addAll(ChargeStatus.fromExternal(externalState));
            }
        }
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
        this.displaySize = displaySize;
        return this;
    }

    public ChargeSearchParams withInternalChargeStatuses(List<ChargeStatus> statuses) {
        this.chargeStatuses = new HashSet<>(statuses);
        return this;
    }

    public String buildQueryParams() {
        StringBuilder builder = new StringBuilder();

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
            builder.append("&state=" + externalChargeState);
        }
        return builder.toString().replaceFirst("&", "");
    }

    private List<ExternalChargeState> parseState(String state) {
        List<ExternalChargeState> externalStates = new ArrayList<>();
        if (isNotBlank(state)) {
            externalStates.addAll(ExternalChargeState.fromStatusString(state));
        }
        return externalStates;
    }
}
