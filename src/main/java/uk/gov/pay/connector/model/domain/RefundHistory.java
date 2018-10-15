package uk.gov.pay.connector.model.domain;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;

import java.sql.Timestamp;
import java.time.ZonedDateTime;

public class RefundHistory extends RefundEntity {

    private ZonedDateTime historyStartDate;
    private ZonedDateTime historyEndDate;

    public RefundHistory(Long id, String externalId, Long amount, String status, Long chargeId, Timestamp createdDate, Long version, String reference, Timestamp historyStartDate, Timestamp historyEndDate, String userExternalId) {
        super();
        setId(id);
        setExternalId(externalId);
        setAmount(amount);
        setStatus(status);

        ChargeEntity charge = new ChargeEntity();
        charge.setId(chargeId);
        setChargeEntity(charge);

        setUserExternalId(userExternalId);
        setCreatedDate(new UTCDateTimeConverter().convertToEntityAttribute(createdDate));
        setVersion(version);
        setReference(reference);

        setHistoryStartDate(new UTCDateTimeConverter().convertToEntityAttribute(historyStartDate));
        setHistoryEndDate(new UTCDateTimeConverter().convertToEntityAttribute(historyEndDate));

    }

    public ZonedDateTime getHistoryStartDate() {
        return historyStartDate;
    }

    public void setHistoryStartDate(ZonedDateTime historyStartDate) {
        this.historyStartDate = historyStartDate;
    }

    public ZonedDateTime getHistoryEndDate() {
        return historyEndDate;
    }

    public void setHistoryEndDate(ZonedDateTime historyEndDate) {
        this.historyEndDate = historyEndDate;
    }
}
