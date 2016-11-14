package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;

@Deprecated
@Entity
@Table(name = "confirmation_details")
@SequenceGenerator(name = "confirmation_details_id_seq", sequenceName = "confirmation_details_id_seq", allocationSize = 1)
public class ConfirmationDetailsEntity extends AbstractEntity {

    @Column(name = "last_digits_card_number")
    @JsonProperty("last_digits_card_number")
    private String lastDigitsCardNumber;
    @Column(name = "cardholder_name")
    @JsonProperty("cardholder_name")
    private String cardHolderName;
    @Column(name = "expiry_date")
    @JsonProperty("expiry_date")
    private String expiryDate;

    @Embedded
    @JsonProperty("billing_address")
    private AddressEntity billingAddress;

    public ChargeEntity getChargeEntity() {
        return chargeEntity;
    }

    protected ConfirmationDetailsEntity() {
    }

    public ConfirmationDetailsEntity(ChargeEntity chargeEntity) {
        this.chargeEntity = chargeEntity;
    }

    @OneToOne
    @JsonIgnore
    @JoinColumn(name = "charge_id", nullable = false, unique = true)
    @JsonManagedReference
    private ChargeEntity chargeEntity;


    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public void setLastDigitsCardNumber(String lastDigitsCardNumber) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolder) {
        this.cardHolderName = cardHolder;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public AddressEntity getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(AddressEntity billingAddress) {
        this.billingAddress = billingAddress;
    }

    public PersistedCard toCard(String cardBrand) {
        PersistedCard card = new PersistedCard();
        card.setLastDigitsCardNumber(lastDigitsCardNumber);
        card.setCardBrand(cardBrand);
        card.setBillingAddress(billingAddress != null ? billingAddress.toAddress() : null);
        card.setExpiryDate(expiryDate);
        card.setCardHolderName(cardHolderName);
        return card;
    }
}
