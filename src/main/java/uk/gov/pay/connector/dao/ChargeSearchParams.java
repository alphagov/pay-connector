package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeState;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ChargeSearchParams {

    private Long gatewayAccountId;
    private String reference;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private Long page;
    private Long displaySize;
    private List<ChargeStatus> chargeStatuses = new LinkedList<>();
    private List<ExternalChargeState> externalChargeStates = new ArrayList<>();

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public ChargeSearchParams withGatewayAccountId(Long gatewayAccountId) {
        this.gatewayAccountId = gatewayAccountId;
        return this;
    }

    public List<ChargeStatus> getChargeStatuses() {
        return chargeStatuses;
    }


    public ChargeSearchParams withExternalChargeState(List<ExternalChargeState> externalStates) {
        if (externalStates != null) {
            this.externalChargeStates = externalStates;
            for (ExternalChargeState externalState : externalStates) {
                this.chargeStatuses.addAll(ChargeStatus.fromExternal(externalState));
            }
        }
        return this;
    }

    public String getReference() {
        return reference;
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
        this.page = (page != null && page >= 1) ? page : 1; // always show first page if its an invalid page request
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
        this.chargeStatuses = statuses;
        return this;
    }

    public String buildQueryParams() {
        StringBuilder builder = new StringBuilder();

        if (StringUtils.isNotBlank(reference))
            builder.append("&reference=" + reference);
        if (fromDate != null)
            builder.append("&from_date=" + fromDate);
        if (toDate != null)
            builder.append("&to_date=" + toDate);
        if (page != null)
            builder.append("&page=" + page);
        if (displaySize != null)
            builder.append("&display_size=" + displaySize);
        if (externalChargeStates != null && !externalChargeStates.isEmpty()) {
            for (ExternalChargeState extState : externalChargeStates) {
                builder.append("&state=" + extState.getStatus());
            }
        }
        return builder.toString();
    }
}
