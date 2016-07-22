package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.persistence.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

@Entity
@Table(name = "refunds")
@SequenceGenerator(name = "refunds_refund_id_seq", sequenceName = "refunds_refund_id_seq", allocationSize = 1)
@Access(AccessType.FIELD)
public class RefundEntity extends AbstractEntity {

    @Column(name = "external_id")
    private String externalId;

    private Long amount;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "charge_id", updatable = false)
    private ChargeEntity chargeEntity;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    public RefundEntity() {
        //for jpa
    }

    public RefundEntity(ChargeEntity chargeEntity, Long amount) {
        this.externalId = RandomIdGenerator.newId();
        this.chargeEntity = chargeEntity;
        this.amount = amount;
        this.status = RefundStatus.CREATED;
        this.createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public String getExternalId() {
        return externalId;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public Long getAmount() {
        return amount;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public void setStatus(RefundStatus status) {
        this.status = status;
    }

    public void setChargeEntity(ChargeEntity chargeEntity) {
        this.chargeEntity = chargeEntity;
    }

    public boolean hasStatus(RefundStatus... status) {
        return Arrays.stream(status).anyMatch(s -> equalsIgnoreCase(s.getValue(), getStatus().getValue()));
    }

}
