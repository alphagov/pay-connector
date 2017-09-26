package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.ZonedDateTime;

public class TransactionEventEntity {
    private enum TransactionStatus {
    }

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "transaction_id", updatable = false)
    private TransactionEntity transactionEntity;

//    @Convert(converter = TransactionStatusConverter.class)
    private TransactionStatus status;

    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime updated;
}

