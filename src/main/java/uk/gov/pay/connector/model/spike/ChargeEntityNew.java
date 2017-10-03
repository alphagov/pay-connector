package uk.gov.pay.connector.model.spike;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import uk.gov.pay.connector.model.domain.Auth3dsDetailsEntity;
import uk.gov.pay.connector.model.domain.CardDetailsEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.time.ZonedDateTime;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;

@Entity
@DiscriminatorValue("CHARGE")
public class ChargeEntityNew extends TransactionEntity {
    @Column(name = "charge_gateway_id")
    private String gatewayTransactionId;

    @Column(name = "charge_email")
    private String email;

    // it represents the 'booked date' from the charge notification
    @Column(name = "charge_gateway_event_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime gatewayEventDate;


    public String getEmail() {
        return email;
    }

    public String getReference() {
        return getPaymentRequest().getReference();
    }
    public String getDescription() {
        return getPaymentRequest().getDescription();
    }
    public String getReturnUrl() {
        return getPaymentRequest().getReturnUrl();
    }
    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public ZonedDateTime getGatewayEventDate() {
        return gatewayEventDate;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayEventDate(ZonedDateTime gatewayEventDate) {
        this.gatewayEventDate = gatewayEventDate;
    }
    public GatewayAccountEntity getGatewayAccount() {
        return getPaymentRequest().getGatewayAccount();
    }
    public ChargeEntityNew() {
    }
    public boolean hasStatus(TransactionStatus... status) {
        return Arrays.stream(status).anyMatch(s -> equalsIgnoreCase(s.getValue(), getStatus()));
    }

    public boolean hasStatus(List<TransactionStatus> status) {
        return hasStatus(status.toArray(new TransactionStatus[0]));
    }
    public ChargeEntityNew(TransactionStatus status, long amount, ZonedDateTime createdDate, CardDetailsEntity cardDetailsEntity, List<TransactionEventEntity> events, PaymentRequestEntity paymentRequestEntity, String gatewayTransactionId, String email,
        ZonedDateTime gatewayEventDate) {
        this.status = status.toString();
        this.setAmount(amount);
        this.setCreatedDate(createdDate);
        this.setEvents(events);
        this.setPaymentRequest(paymentRequestEntity);
        this.setCardDetails(cardDetailsEntity);
        this.gatewayTransactionId = gatewayTransactionId;
        this.email = email;
        this.gatewayEventDate = gatewayEventDate;
    }

    public ChargeEntityNew(TransactionStatus status, long amount, ZonedDateTime createdDate, CardDetailsEntity cardDetailsEntity, List<TransactionEventEntity> events, PaymentRequestEntity paymentRequestEntity, String gatewayTransactionId, String email,
        ZonedDateTime gatewayEventDate, Auth3dsDetailsEntity auth3dsDetailsEntity) {
        this(status, amount, createdDate, cardDetailsEntity, events, paymentRequestEntity, gatewayTransactionId, email, gatewayEventDate);
        this.setAuth3dsDetails(auth3dsDetailsEntity);
    }
}
