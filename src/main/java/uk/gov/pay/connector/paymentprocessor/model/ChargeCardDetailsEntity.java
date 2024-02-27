package uk.gov.pay.connector.paymentprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import uk.gov.pay.connector.charge.model.CardDetailsEntity;
import uk.gov.pay.connector.charge.model.domain.Auth3dsRequiredEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.common.model.domain.AbstractVersionedEntity;

import javax.persistence.*;

@Entity
@Table(name = "charge_card_details")
@Access(AccessType.FIELD)
@SequenceGenerator(name = "charge_card_details_id_seq",
        sequenceName = "charge_card_details_id_seq", allocationSize = 1)
public class ChargeCardDetailsEntity extends AbstractVersionedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "charge_card_details_id_seq")
    private Long id;

    @JoinColumn(name = "charge_id", updatable = false, insertable = false)
    @JsonIgnore
    private ChargeEntity gatewayAccountEntity;

    @Embedded
    private CardDetailsEntity cardDetails;

    @Embedded
    private Auth3dsRequiredEntity auth3dsRequiredDetails;
    
    public CardDetailsEntity getCardDetails() {
        return cardDetails;
    }

    public void setCardDetails(CardDetailsEntity cardDetailsEntity) {
        this.cardDetails = cardDetailsEntity;
    }

    public Auth3dsRequiredEntity get3dsRequiredDetails() {
        return auth3dsRequiredDetails;
    }

    public void set3dsRequiredDetails(Auth3dsRequiredEntity auth3dsRequiredDetails) {
        this.auth3dsRequiredDetails = auth3dsRequiredDetails;
    }
}
