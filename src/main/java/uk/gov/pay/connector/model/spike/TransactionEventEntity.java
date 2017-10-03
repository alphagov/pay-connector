package uk.gov.pay.connector.model.spike;

import static java.util.Arrays.stream;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CANCELLED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_CREATED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_ERROR_GATEWAY;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_CANCELLED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_EXPIRED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_FAILED_REJECTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_STARTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUBMITTED;
import static uk.gov.pay.connector.model.api.ExternalChargeState.EXTERNAL_SUCCESS;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
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
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.api.ExternalChargeState;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

@Entity
@Table(name = "transaction_events")
@SequenceGenerator(name = "transaction_events_transaction_id_seq", sequenceName = "transaction_events_transaction_id_seq", allocationSize = 1)
public class TransactionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    protected Long id;

    public enum TransactionStatus {
        CREATED("CREATED", EXTERNAL_CREATED),
        ENTERING_CARD_DETAILS("ENTERING CARD DETAILS", EXTERNAL_STARTED),
        AUTHORISATION_ABORTED("AUTHORISATION ABORTED", EXTERNAL_FAILED_REJECTED),
        AUTHORISATION_READY("AUTHORISATION READY", EXTERNAL_STARTED),

        AUTHORISATION_3DS_REQUIRED("AUTHORISATION 3DS REQUIRED", EXTERNAL_STARTED),
        AUTHORISATION_3DS_READY("AUTHORISATION 3DS READY", EXTERNAL_STARTED),


        AUTHORISATION_SUBMITTED("AUTHORISATION SUBMITTED", EXTERNAL_ERROR_GATEWAY),
        AUTHORISATION_SUCCESS("AUTHORISATION SUCCESS", EXTERNAL_SUBMITTED),
        AUTHORISATION_REJECTED("AUTHORISATION REJECTED", EXTERNAL_FAILED_REJECTED),
        AUTHORISATION_CANCELLED("AUTHORISATION CANCELLED", EXTERNAL_FAILED_REJECTED),
        AUTHORISATION_ERROR("AUTHORISATION ERROR", EXTERNAL_ERROR_GATEWAY),
        AUTHORISATION_TIMEOUT("AUTHORISATION TIMEOUT", EXTERNAL_ERROR_GATEWAY),
        AUTHORISATION_UNEXPECTED_ERROR("AUTHORISATION UNEXPECTED ERROR", EXTERNAL_ERROR_GATEWAY),

        CAPTURE_APPROVED("CAPTURE APPROVED", EXTERNAL_SUCCESS),
        CAPTURE_APPROVED_RETRY("CAPTURE APPROVED RETRY", EXTERNAL_SUCCESS),
        CAPTURE_READY("CAPTURE READY", EXTERNAL_SUCCESS),
        CAPTURED("CAPTURED", EXTERNAL_SUCCESS),
        CAPTURE_SUBMITTED("CAPTURE SUBMITTED", EXTERNAL_SUCCESS),
        CAPTURE_ERROR("CAPTURE ERROR", EXTERNAL_ERROR_GATEWAY),

        EXPIRE_CANCEL_READY("EXPIRE CANCEL READY", EXTERNAL_FAILED_EXPIRED),
        EXPIRE_CANCEL_FAILED("EXPIRE CANCEL FAILED", EXTERNAL_FAILED_EXPIRED),
        EXPIRE_CANCEL_SUBMITTED("EXPIRE CANCEL SUBMITTED", EXTERNAL_FAILED_EXPIRED),
        EXPIRED("EXPIRED", EXTERNAL_FAILED_EXPIRED),

        SYSTEM_CANCEL_READY("SYSTEM CANCEL READY", EXTERNAL_CANCELLED),
        SYSTEM_CANCEL_ERROR("SYSTEM CANCEL ERROR", EXTERNAL_CANCELLED),
        SYSTEM_CANCEL_SUBMITTED("SYSTEM CANCEL SUBMITTED", EXTERNAL_CANCELLED),
        SYSTEM_CANCELLED("SYSTEM CANCELLED", EXTERNAL_CANCELLED),

        USER_CANCEL_READY("USER CANCEL READY", EXTERNAL_FAILED_CANCELLED),
        USER_CANCEL_SUBMITTED("USER CANCEL SUBMITTED", EXTERNAL_FAILED_CANCELLED),
        USER_CANCELLED("USER CANCELLED", EXTERNAL_FAILED_CANCELLED),
        USER_CANCEL_ERROR("USER CANCEL ERROR", EXTERNAL_FAILED_CANCELLED),


        REFUND_CREATED("REFUND CREATED", EXTERNAL_SUBMITTED);

        private String value;
        private ExternalChargeState externalStatus;

        TransactionStatus(String value, ExternalChargeState externalStatus) {
            this.value = value;
            this.externalStatus = externalStatus;
        }

        public String getValue() {
            return value;
        }

        public String toString() {
            return this.getValue();
        }

        public ExternalChargeState toExternal() {
            return externalStatus;
        }

        public static TransactionStatus fromString(String status) {
            for (TransactionStatus stat : values()) {
                if (StringUtils.equals(stat.getValue(), status)) {
                    return stat;
                }
            }
            throw new IllegalArgumentException("charge status not recognized: " + status);
        }
        public static List<TransactionStatus> fromExternal(ExternalChargeState externalStatus) {
            return stream(values()).filter(status -> status.toExternal().equals(externalStatus)).collect(
                Collectors.toList());
        }

    }

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "transaction_id", updatable = false)
    private TransactionEntity transactionEntity;

//    @Convert(converter = TransactionStatusConverter.class)
    private TransactionStatus status;

    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;

    protected TransactionEventEntity() {}
    public TransactionEventEntity(TransactionEntity chargeEntity, TransactionStatus chargeStatus, ZonedDateTime updated) {
        this.transactionEntity = chargeEntity;
        this.status = chargeStatus;
        this.updated = updated;
    }

    public static TransactionEventEntity from(TransactionEntity chargeEntity, TransactionStatus chargeStatus, ZonedDateTime updated) {
        return new TransactionEventEntity(chargeEntity, chargeStatus, updated);
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public ZonedDateTime getUpdated() {
        return updated;
    }
}

