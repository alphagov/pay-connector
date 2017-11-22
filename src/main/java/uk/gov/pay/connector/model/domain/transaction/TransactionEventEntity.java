package uk.gov.pay.connector.model.domain.transaction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.model.domain.Status;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "transaction_events")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="operation")
@SequenceGenerator(name = "transaction_events_id_seq",
        sequenceName = "transaction_events_id_seq",
        allocationSize = 1)
public abstract class TransactionEventEntity<E extends Status> extends AbstractVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transactions_id_seq")
    @JsonIgnore
    private Long id;

    @ManyToOne
    @JoinColumn(name = "transaction_id", referencedColumnName = "id", updatable = false)
    private TransactionEntity<E> transaction;

    @Column(name = "updated")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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
