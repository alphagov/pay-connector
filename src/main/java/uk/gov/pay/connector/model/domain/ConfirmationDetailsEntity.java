package uk.gov.pay.connector.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.*;

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
    private Address billingAddress;

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
    @JoinColumn(name = "charge_id", nullable = false, unique=true)
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

    public Address getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfirmationDetailsEntity that = (ConfirmationDetailsEntity) o;

        if (!lastDigitsCardNumber.equals(that.lastDigitsCardNumber)) return false;
        if (!cardHolderName.equals(that.cardHolderName)) return false;
        if (!expiryDate.equals(that.expiryDate)) return false;
        if (!billingAddress.equals(that.billingAddress)) return false;
        return chargeEntity.equals(that.chargeEntity);

    }

    @Override
    public int hashCode() {
        int result = lastDigitsCardNumber.hashCode();
        result = 31 * result + cardHolderName.hashCode();
        result = 31 * result + expiryDate.hashCode();
        result = 31 * result + billingAddress.hashCode();
        result = 31 * result + chargeEntity.hashCode();
        return result;
    }
}
