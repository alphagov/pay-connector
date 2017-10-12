package uk.gov.pay.connector.model.domain.newmodel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class NewModel {

    public static class PaymentRequestEntity {
        @Column(name = "external_id")
        private String externalId;

        @Column(name = "amount")
        private Long amount;

        @Column(name = "reference")
        private String reference;

        @Column(name = "description")
        private String description;

        @Column(name = "return_url")
        private String returnUrl;


        @ManyToOne
        @JoinColumn(name = "gateway_account_id", updatable = false)
        private GatewayAccountEntity gatewayAccount;
    }


    public static class TransactionEntity {
        @Id
        @JsonIgnore
        private Long id;

        // TODO: question are we doing optimistic locking on charges at present?
        @Version
        @Column(name = "version")
        private Long version;

        // Question: Charges currently have an external id, but we probably want to move that to
        // PaymentRequestEntity, should we generate new random externalid values for the charge
        // transaction rows
        @Column(name = "external_id")
        private String externalId;

        @Column(name = "status")
        private String status;

        @Column(name = "created_date")
        @Convert(converter = UTCDateTimeConverter.class)
        private ZonedDateTime createdDate;

        @OrderBy("updated DESC")
        private List<TransactionEventEntity> events = new ArrayList<>();

        // Maybe not a good idea? Could allow us to use this as a 'discriminator'
        // in single table inheritance
        private PaymentGatewayName paymentProvider;
    }

    public static class ChargeEntity extends TransactionEntity {
        @Column(name = "gateway_transaction_id")
        private String gatewayTransactionId;

        @Column(name = "email")
        private String email;

        @Embedded
        private CardDetailsEntity cardDetails;

        @Embedded
        private Auth3dsDetailsEntity auth3dsDetails;

        @Column(name = "provider_session_id")
        private String providerSessionId;
    }

    public static class RefundEntity extends TransactionEntity {
        // Note: Was 'reference'
        // TODO: check whether there are any other kind of refund references
        @Column(name = "psp_id")
        private String psp_id;

        private Long epdq_pay_id_sub;

        private Long refund_amount;
        @Column(name = "refunded_by")
        private String refunded_by;
    }

    private static class TransactionEventEntity {

        @JsonIgnore
        @ManyToOne
        @JoinColumn(name = "transaction_id", updatable = false)
        private TransactionEntity transactionEntity;

        @Convert(converter = TransactionStatusConverter.class)
        private TransactionStatus status;

        // TODO: decide whether this belongs on charge instead
        // it represents the 'booked date' from the charge notification
        @Column(name = "gateway_event_date")
        @Convert(converter = UTCDateTimeConverter.class)
        private ZonedDateTime gatewayEventDate;

        @Convert(converter = UTCDateTimeConverter.class)
        private ZonedDateTime updated;
    }

    private enum TransactionStatus {
    }
}

