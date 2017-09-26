package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.AbstractEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OrderBy;
import javax.persistence.Version;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionEntity extends AbstractEntity {
    private enum Operation {
        REFUND, CHARGE
    }
    @Id
    @JsonIgnore
    private Long id;

    // TODO: question are we doing optimistic locking on charges at present?
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "status")
    private String status;

    @Column(name = "operation")
    protected Operation operation;

    @Column(name = "amount")
    private BigInteger amount;

    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;

    @OrderBy("updated DESC")
    private List<TransactionEventEntity> events = new ArrayList<>();

    // Maybe not a good idea? Could allow us to use this as a 'discriminator'
    // in single table inheritance
    private PaymentGatewayName paymentProvider;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "payment_request_id", updatable = false)
    private PaymentRequestEntity paymentRequest;
}
