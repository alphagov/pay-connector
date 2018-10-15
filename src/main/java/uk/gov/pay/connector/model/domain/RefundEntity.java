package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.ColumnResult;
import javax.persistence.ConstructorResult;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

@SqlResultSetMapping(
        name = "RefundEntityHistoryMapping",
        classes = @ConstructorResult(
                targetClass = RefundHistory.class,
                columns = {
                        @ColumnResult(name = "id", type = Long.class),
                        @ColumnResult(name = "external_id", type = String.class),
                        @ColumnResult(name = "amount", type = Long.class),
                        @ColumnResult(name = "status", type = String.class),
                        @ColumnResult(name = "charge_id", type = Long.class),
                        @ColumnResult(name = "created_date", type = Timestamp.class),
                        @ColumnResult(name = "version", type=Long.class),
                        @ColumnResult(name = "reference", type = String.class),
                        @ColumnResult(name = "history_start_date", type = Timestamp.class),
                        @ColumnResult(name = "history_end_date", type = Timestamp.class),
                        @ColumnResult(name = "user_external_id", type = String.class)
                }))

@Entity
@Table(name = "refunds")
@Customizer(HistoryCustomizer.class)
@Access(AccessType.FIELD)
public class RefundEntity extends AbstractVersionedEntity {

    @Id
    @SequenceGenerator(name = "refundsSequence", sequenceName = "refunds_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="refundsSequence")
    @JsonIgnore
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "reference")
    private String reference;

    private Long amount;

    private String status;

    @ManyToOne
    @JoinColumn(name = "charge_id")
    private ChargeEntity chargeEntity;

    @Column(name = "user_external_id")
    private String userExternalId;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    public RefundEntity() {
        //for jpa
    }

    public RefundEntity(ChargeEntity chargeEntity, Long amount, String userExternalId) {
        this.externalId = RandomIdGenerator.newId();
        this.chargeEntity = chargeEntity;
        this.amount = amount;
        setStatus(RefundStatus.CREATED);
        this.createdDate = ZonedDateTime.now(ZoneId.of("UTC"));
        this.userExternalId = userExternalId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getReference() {
        return reference;
    }

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    public Long getAmount() {
        return amount;
    }

    public RefundStatus getStatus() {
        return RefundStatus.fromString(status);
    }

    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public void setStatus(RefundStatus status) {
        this.status = status.getValue();
    }

    protected void setChargeEntity(ChargeEntity chargeEntity) {
        this.chargeEntity = chargeEntity;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public boolean hasStatus(RefundStatus... status) {
        return Arrays.stream(status).anyMatch(s -> equalsIgnoreCase(s.getValue(), getStatus().getValue()));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserExternalId() {
        return userExternalId;
    }

    public void setUserExternalId(String userExternalId) {
        this.userExternalId = userExternalId;
    }

    @Override
    public String toString() {
        return "RefundEntity{" +
                "externalId='" + externalId + '\'' +
                ", reference='" + reference + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", userExternalId='" + userExternalId + '\'' +
                ", chargeEntity=" + chargeEntity +
                ", createdDate=" + createdDate +
                '}';
    }
}
