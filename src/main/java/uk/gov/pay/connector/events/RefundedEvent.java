package uk.gov.pay.connector.events;

import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "refunded_events")
@Access(AccessType.FIELD)
public class RefundedEvent {
    
    @Column(name = "gateway_account_id")
    private Long gatewayAccountId;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @Column(name = "amount")
    private Long amount;

    @Id
    @Column(name = "external_id")
    private String externalId;

    public RefundedEvent() { //for jpa
    }

    public RefundedEvent(Long gatewayAccountId, ZonedDateTime createdDate, Long amount, String externalId) {
        this.gatewayAccountId = gatewayAccountId;
        this.createdDate = createdDate;
        this.amount = amount;
        this.externalId = externalId;
    }

    public Long getGatewayAccountId() {
        return gatewayAccountId;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public Long getAmount() {
        return amount;
    }

    public String getExternalId() {
        return externalId;
    }
}
