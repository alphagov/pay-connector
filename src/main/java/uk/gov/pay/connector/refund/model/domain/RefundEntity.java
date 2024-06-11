package uk.gov.pay.connector.refund.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;
import uk.gov.pay.connector.common.model.domain.HistoryCustomizer;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.ColumnResult;
import javax.persistence.ConstructorResult;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static java.time.temporal.ChronoUnit.MICROS;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@SqlResultSetMapping(
        name = "RefundEntityHistoryMapping",
        classes = @ConstructorResult(
                targetClass = RefundHistory.class,
                columns = {
                        @ColumnResult(name = "id", type = Long.class),
                        @ColumnResult(name = "external_id", type = String.class),
                        @ColumnResult(name = "amount", type = Long.class),
                        @ColumnResult(name = "status", type = String.class),
                        @ColumnResult(name = "created_date", type = Timestamp.class),
                        @ColumnResult(name = "version", type=Long.class),
                        @ColumnResult(name = "history_start_date", type = Timestamp.class),
                        @ColumnResult(name = "history_end_date", type = Timestamp.class),
                        @ColumnResult(name = "user_external_id", type = String.class),
                        @ColumnResult(name = "gateway_transaction_id", type = String.class),
                        @ColumnResult(name = "charge_external_id", type = String.class),
                        @ColumnResult(name = "user_email", type = String.class)
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

    private Long amount;

    private String status;

    @Column(name = "user_external_id")
    private String userExternalId;
    
    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @Column(name = "charge_external_id")
    private String chargeExternalId;

    @Column(name = "parity_check_status")
    @Enumerated(EnumType.STRING)
    private ParityCheckStatus parityCheckStatus;

    @Column(name = "parity_check_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime parityCheckDate;

    public RefundEntity() {
        //for jpa
    }

    public RefundEntity(Long amount, String userExternalId, String userEmail, String chargeExternalId) {
        this.externalId = RandomIdGenerator.newId();
        this.amount = amount;
        this.createdDate = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(MICROS);
        this.userExternalId = userExternalId;
        this.userEmail = userEmail;
        this.chargeExternalId = chargeExternalId;
    }

    public String getExternalId() {
        return externalId;
    }
    
    public String getGatewayTransactionId() { 
        return gatewayTransactionId;
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

    public void setAmount(Long amount) {
        this.amount = amount;
    }

    public void setStatus(RefundStatus status) {
        this.status = status.getValue();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
    
    public void setGatewayTransactionId(String gatewayTransactionId) { 
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public boolean hasStatus(RefundStatus... status) {
        return Arrays.stream(status).anyMatch(s -> equalsIgnoreCase(s.getValue(), getStatus().getValue()));
    }

    public boolean hasStatus() {
        return isNotBlank(status);
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

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    @Override
    public String toString() {
        return "RefundEntity{" +
                "externalId='" + externalId + '\'' +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                ", userExternalId='" + userExternalId + '\'' +
                ", gatewayTransactionId=" + gatewayTransactionId +
                ", chargeExternalId=" + chargeExternalId +
                ", createdDate=" + createdDate +
                '}';
    }

    public String getChargeExternalId() {
        return chargeExternalId;
    }

    public void setChargeExternalId(String chargeExternalId) {
        this.chargeExternalId = chargeExternalId;
    }

    public ParityCheckStatus getParityCheckStatus() {
        return parityCheckStatus;
    }

    public void setParityCheckStatus(ParityCheckStatus parityCheckStatus) {
        this.parityCheckStatus = parityCheckStatus;
    }

    public ZonedDateTime getParityCheckDate() {
        return parityCheckDate;
    }

    public void setParityCheckDate(ZonedDateTime parityCheckDate) {
        this.parityCheckDate = parityCheckDate;
    }
}
