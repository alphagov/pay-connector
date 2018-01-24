package uk.gov.pay.connector.model.domain.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.Card3dsEntity;
import uk.gov.pay.connector.model.domain.CardEntity;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.model.domain.UTCDateTimeConverter;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.OrderBy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;

@Entity
@DiscriminatorValue(value = "CHARGE")
public class ChargeTransactionEntity extends TransactionEntity<ChargeStatus, ChargeTransactionEventEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ChargeTransactionEntity.class);

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    protected ChargeStatus status;
    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    @OrderBy("updated DESC")
    private List<ChargeTransactionEventEntity> transactionEvents = new ArrayList<>();
    @OneToOne(mappedBy = "chargeTransactionEntity", cascade = CascadeType.PERSIST)
    private CardEntity card;
    @OneToOne(mappedBy = "chargeTransactionEntity", cascade = CascadeType.PERSIST)
    private Card3dsEntity card3ds;
    @Column(name = "created_date")
    @Convert(converter = UTCDateTimeConverter.class)
    private ZonedDateTime createdDate;
    @Column(name = "email")
    private String email;

    public ChargeTransactionEntity() {
        super(TransactionOperation.CHARGE);
    }

    @Override
    public ChargeStatus getStatus() {
        return status;
    }

    @Override
    void setStatus(ChargeStatus status) {
        this.status = status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public CardEntity getCard() {
        return card;
    }

    public void setCard(CardEntity card) {
        this.card = card;
        card.setChargeTransactionEntity(this);
    }

    public Card3dsEntity getCard3ds() {
        return card3ds;
    }

    public void setCard3ds(Card3dsEntity card3ds) {
        this.card3ds = card3ds;
        card3ds.setChargeTransactionEntity(this);
    }

    public void updateStatus(ChargeStatus newStatus, ZonedDateTime gatewayEventTime) {
        ChargeTransactionEventEntity transactionEvent = createNewTransactionEvent();
        transactionEvent.setGatewayEventDate(gatewayEventTime);
        updateStatus(newStatus, transactionEvent);
    }

    @Override
    protected ChargeTransactionEventEntity createNewTransactionEvent() {
        return new ChargeTransactionEventEntity();
    }

    @Override
    void updateStatus(ChargeStatus newStatus, ChargeTransactionEventEntity transactionEvent) {
        if (this.status != null && !PaymentGatewayStateTransitions.getInstance().isValidTransition(this.status, newStatus)) {
            logger.warn(
                    format("Charge state transition [%s] -> [%s] not allowed for externalId [%s] transactionId [%s]",
                            this.status.getValue(),
                            newStatus.getValue(),
                            (getPaymentRequest() != null) ? getPaymentRequest().getExternalId() : "not set",
                            getId()
                    )
            );
        }
        super.updateStatus(newStatus, transactionEvent);
    }

    public static ChargeTransactionEntity from(ChargeEntity chargeEntity) {
        ChargeTransactionEntity transactionEntity = new ChargeTransactionEntity();
        transactionEntity.setGatewayTransactionId(chargeEntity.getGatewayTransactionId());
        transactionEntity.setAmount(chargeEntity.getAmount());
        transactionEntity.updateStatus(ChargeStatus.fromString(chargeEntity.getStatus()));
        transactionEntity.setCreatedDate(chargeEntity.getCreatedDate());

        return transactionEntity;
    }

    public List<ChargeTransactionEventEntity> getTransactionEvents() {
        return transactionEvents;
    }

    @Override
    public ZonedDateTime getCreatedDate() {
        return createdDate;
    }

    @Override
    public void setCreatedDate(ZonedDateTime createdDate) {
        this.createdDate = createdDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
