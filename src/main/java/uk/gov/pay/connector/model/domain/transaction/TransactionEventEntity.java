package uk.gov.pay.connector.model.domain.transaction;

import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "transaction_events")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="operation")
public abstract class TransactionEventEntity<E extends Status> extends AbstractEntity {
    @ManyToOne
    @JoinColumn(name = "transaction_id", referencedColumnName = "id", updatable = false)
    private TransactionEntity<E> transaction;

    @Column(name = "updated")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;

    public TransactionEntity<E> getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity<E> transaction) {
        this.transaction = transaction;
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }

    public void setUpdated(ZonedDateTime updated) {
        this.updated = updated;
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    public void setGatewayEventDate(ZonedDateTime gatewayEventDate) {
        this.gatewayEventDate = gatewayEventDate;
    }

    public abstract void setStatus(E status);
    public abstract E getStatus();
}
