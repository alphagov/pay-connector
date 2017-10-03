package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.eclipse.persistence.annotations.Customizer;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.HistoryCustomizer;
import uk.gov.pay.connector.model.domain.RefundHistory;
import uk.gov.pay.connector.model.domain.RefundStatus;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;
import uk.gov.pay.connector.util.RandomIdGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
@Entity
@DiscriminatorValue("REFUND")
public class RefundEntityNew extends TransactionEntity{
    @Column(name = "refund_external_id")
    private String externalId;

    @Column(name = "refund_smartpay_pspreference")
    private String smartpayPspReference;

    @Column(name = "refund_epdq_payid")
    private String epdqPayId;

    @Column(name = "refund_epdq_payidsub")
    private String epdqPayIdSub;

    @Column(name = "refunded_by")
    private String refundedBy;

    public String getExternalId() {
        return externalId;
    }

    public String getSmartpayPspReference() {
        return smartpayPspReference;
    }

    public String getEpdqPayId() {
        return epdqPayId;
    }

    public String getEpdqPayIdSub() {
        return epdqPayIdSub;
    }

    public String getRefundedBy() {
        return refundedBy;
    }

    public RefundEntityNew(
        PaymentRequestEntity paymentRequest,
        long amount,
        TransactionStatus status,
        ZonedDateTime createdDate,
        String refundSmartpayPspReference,
        String refundEpdqPayId,
        String refundEpdqPayIdSub,
        String refundedBy
    ) {
        this.externalId = RandomIdGenerator.newId();
        this.setAmount(amount);
        this.setCreatedDate(createdDate);
        this.setPaymentRequest(paymentRequest);
        this.status = status.toString();
        this.smartpayPspReference = refundSmartpayPspReference;
        this.epdqPayId = refundEpdqPayId;
        this.epdqPayIdSub = refundEpdqPayIdSub;
        this.refundedBy = refundedBy;
    }

    public RefundEntityNew() {
    }
}
