package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.api.LegacyChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ChargeSearchParams {

    private Long gatewayAccountId;
    private String reference;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private Long page = 1L;
    private Long displaySize = 100L;
    private List<ChargeStatus> chargeStatuses = new LinkedList<>();

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
            for (ExternalChargeState externalState : externalStates) {
                this.chargeStatuses.addAll(ChargeStatus.fromExternal(externalState));
            }
        }

        return this;
    }


    public ChargeSearchParams withLegacyChargeStatus(LegacyChargeStatus externalChargeStatus) {
        if (externalChargeStatus != null) {
            this.chargeStatuses.addAll(ChargeStatus.fromLegacy(externalChargeStatus));
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
        return page - 1; // 1 based page and 0 based offset
    }

    public ChargeSearchParams withPage(Long page) {
        if (page != null && page >= 0)
            this.page = page;
        return this;
    }

    public Long getDisplaySize() {
        return displaySize;
    }

    public ChargeSearchParams withDisplaySize(Long displaySize) {
        if (displaySize != null && displaySize >= 0)
            this.displaySize = displaySize;
        return this;
    }

    public ChargeSearchParams withInternalChargeStatuses(List<ChargeStatus> statuses) {
        this.chargeStatuses = statuses;
        return this;
    }
}
