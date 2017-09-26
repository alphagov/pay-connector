package uk.gov.pay.connector.model.spike;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.ZonedDateTime;

public class ChargeEntityNew extends TransactionEntity {
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Embedded
    private Auth3dsDetailsEntity auth3dsDetails;

    //todo: does these belong to the payment request?
    @Embedded
    private CardDetailsEntity cardDetails;

    @Column(name = "email")
    private String email;

    // it represents the 'booked date' from the charge notification
    @Column(name = "gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;
}
