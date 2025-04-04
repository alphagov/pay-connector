package uk.gov.pay.connector.paymentinstrument.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.common.model.api.ToLowerCaseStringSerializer;
import uk.gov.pay.connector.gatewayaccount.util.JsonToStringStringMapConverter;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.service.payments.commons.jpa.InstantToUtcTimestampWithoutTimeZoneConverter;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_INSTRUMENT_EXTERNAL_ID;


@Entity
@Table(name = "payment_instruments")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "payment_instruments_id_seq", sequenceName = "payment_instruments_id_seq", allocationSize = 1)
public class PaymentInstrumentEntity {

    private final static Logger logger = LoggerFactory.getLogger(PaymentInstrumentEntity.class);

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_instruments_id_seq")
    private Long id;

    @Column(name = "recurring_auth_token", columnDefinition = "json")
    @Convert(converter = JsonToStringStringMapConverter.class)
    private Map<String, String> recurringAuthToken;

    @Column(name = "created_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant createdDate;

    @Column(name = "start_date")
    @Convert(converter = InstantToUtcTimestampWithoutTimeZoneConverter.class)
    private Instant startDate;

    @Column(name = "external_id")
    private String externalId;
    
    @Column(name = "agreement_external_id")
    private String agreementExternalId;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    @JsonSerialize(using = ToLowerCaseStringSerializer.class)
    private PaymentInstrumentStatus status;

    @Embedded
    private CardDetailsEntity cardDetails;

    private PaymentInstrumentEntity(Instant createdDate, Map<String, String> recurringAuthToken, Instant startDate, CardDetailsEntity cardDetails, PaymentInstrumentStatus status) {
        this.createdDate = createdDate;
        this.externalId = RandomIdGenerator.newId();
        this.recurringAuthToken = recurringAuthToken;
        this.startDate = startDate;
        this.cardDetails = cardDetails;
        this.status = status;
    }

    public Optional<Map<String, String>> getRecurringAuthToken() {
        return Optional.ofNullable(recurringAuthToken);
    }

    public void setRecurringAuthToken(Map<String, String> recurringAuthToken) {
        this.recurringAuthToken = recurringAuthToken;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public PaymentInstrumentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentInstrumentStatus newStatus) {
        logger.info(format("Changing payment instrument status for externalId [%s] [%s]->[%s]", this.externalId, this.status, newStatus),
                kv(PAYMENT_INSTRUMENT_EXTERNAL_ID, this.externalId),
                kv("from_state", this.status),
                kv("to_state", newStatus));
        this.status = newStatus;
    }

    public CardDetailsEntity getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails(CardDetailsEntity cardDetails) {
        this.cardDetails = cardDetails;
    }

    public String getAgreementExternalId() {
        return agreementExternalId;
    }

    public void setAgreementExternalId(String agreementExternalId) {
        this.agreementExternalId = agreementExternalId;
    }

    public PaymentInstrumentEntity() {
        // For JPA
    }

    public static class PaymentInstrumentEntityBuilder {
        private CardDetailsEntity cardDetails;
        private Instant createdDate;
        private PaymentInstrumentStatus status;
        private Instant startDate;
        private Map<String, String> recurringAuthToken;

        public static PaymentInstrumentEntity.PaymentInstrumentEntityBuilder aPaymentInstrumentEntity(Instant createdDate) {
            var paymentInstrumentEntityBuilder = new PaymentInstrumentEntityBuilder();
            paymentInstrumentEntityBuilder.withCreatedDate(createdDate);
            return paymentInstrumentEntityBuilder;
        }

        public PaymentInstrumentEntity.PaymentInstrumentEntityBuilder withCardDetails(CardDetailsEntity cardDetails) {
            this.cardDetails = cardDetails;
            return this;
        }

        public PaymentInstrumentEntity.PaymentInstrumentEntityBuilder withCreatedDate(Instant createdDate) {
            this.createdDate = createdDate;
            return this;
        }

        public PaymentInstrumentEntity.PaymentInstrumentEntityBuilder withStartDate(Instant startDate) {
            this.startDate = startDate;
            return this;
        }

        public PaymentInstrumentEntity.PaymentInstrumentEntityBuilder withStatus(PaymentInstrumentStatus status) {
            this.status = status;
            return this;
        }

        public PaymentInstrumentEntity.PaymentInstrumentEntityBuilder withRecurringAuthToken(Map<String, String> recurringAuthToken) {
            this.recurringAuthToken = recurringAuthToken;
            return this;
        }


        public PaymentInstrumentEntity build() {
            return new PaymentInstrumentEntity(createdDate, recurringAuthToken, startDate, cardDetails, status);
        }
    }

}
