package uk.gov.pay.connector.charge.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import java.time.Instant;
import java.time.ZonedDateTime;

@Entity
@Table(name = "fees")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charges_charge_id_seq",
        sequenceName = "charges_charge_id_seq", allocationSize = 1)
public class FeeEntity {
    
    public FeeEntity() {
    }

    public FeeEntity(ChargeEntity chargeEntity, Instant createdDate, Long amount, FeeType feeType) {
        this.externalId = RandomIdGenerator.newId();
        this.chargeEntity = chargeEntity;
        this.amountDue = amount;
        this.amountCollected = amount;
        this.createdDate = createdDate;
        this.feeType = feeType;
    }

    public FeeEntity(ChargeEntity chargeEntity, Instant createdDate, Fee fee) {
        this(chargeEntity, createdDate, fee.amount(), fee.feeType());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charges_charge_id_seq")
    @JsonIgnore
    private long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "fee_type")
    @Convert(converter = FeeTypeConverter.class)
    private FeeType feeType;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Column(name = "amount_due")
    private long amountDue;

    @Column(name = "amount_collected")
    private long amountCollected;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    @Column(name = "collected_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime collectedDate;
    
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;
    
    public long getAmountCollected() {
        return amountCollected;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public void setFeeType(FeeType feeType) {
        this.feeType = feeType;
    }
}
