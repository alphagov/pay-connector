package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.cardtype.model.domain.CardBrandLabelEntity;
import uk.gov.pay.connector.charge.model.AddressEntity;
import uk.gov.pay.connector.charge.model.FirstDigitsCardNumber;
import uk.gov.pay.connector.charge.model.LastDigitsCardNumber;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.CorporateCardSurchargeCalculator;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;
import java.util.Optional;

public class PaymentDetailsEnteredEventDetails extends EventDetails {

    private final Long corporateSurcharge;
    private final String email;
    private final String cardBrand;
    private final String cardBrandLabel;
    private final String firstDigitsCardNumber;
    private final String lastDigitsCardNumber;
    private final String gatewayTransactionId;
    private final String cardholderName;
    private final String expiryDate;
    private final String addressLine1;
    private final String addressLine2;
    private final String addressPostcode;
    private final String addressCity;
    private final String addressCounty;
    private final String addressCountry;
    private final String wallet;
    private final Long totalAmount;

    private PaymentDetailsEnteredEventDetails(Long corporateSurcharge, String email, String cardBrand,
                                              String cardBrandLabel, String gatewayTransactionId, String firstDigitsCardNumber,
                                              String lastDigitsCardNumber, String cardholderName, String expiryDate,
                                              String addressLine1, String addressLine2, String addressPostcode,
                                              String addressCity, String addressCounty, String addressCountry,
                                              String wallet, Long totalAmount) {

        this.corporateSurcharge = corporateSurcharge;
        this.email = email;
        this.cardBrand = cardBrand;
        this.cardBrandLabel = cardBrandLabel;
        this.gatewayTransactionId = gatewayTransactionId;
        this.firstDigitsCardNumber = firstDigitsCardNumber;
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.cardholderName = cardholderName;
        this.expiryDate = expiryDate;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.addressPostcode = addressPostcode;
        this.addressCity = addressCity;
        this.addressCounty = addressCounty;
        this.addressCountry = addressCountry;
        this.wallet = wallet;
        this.totalAmount = totalAmount;
    }

    public static PaymentDetailsEnteredEventDetails from(ChargeEntity charge) {
        Builder builder = new Builder()
                .withEmail(charge.getEmail())
                .withGatewayTransactionId(charge.getGatewayTransactionId())
                .withCorporateSurcharge(charge.getCorporateSurcharge().orElse(null))
                .withTotalAmount(CorporateCardSurchargeCalculator.getTotalAmountFor(charge))
                .withWallet(Optional.ofNullable(charge.getWalletType()).map(Enum::toString).orElse(null));
        
        Optional.ofNullable(charge.getCardDetails()).ifPresent(
                cardDetails -> 
                    builder.withCardBrand(cardDetails.getCardBrand())
                            .withCardBrandLabel(cardDetails.getCardTypeDetails().map(CardBrandLabelEntity::getLabel).orElse(null))
                            .withFirstDigitsCardNumber(Optional.ofNullable(cardDetails.getFirstDigitsCardNumber())
                                    .map(FirstDigitsCardNumber::toString)
                                    .orElse(null)
                            )
                            .withLastDigitsCardNumber(Optional.ofNullable(cardDetails.getLastDigitsCardNumber())
                                    .map(LastDigitsCardNumber::toString)
                                    .orElse(null)
                            )
                            .withCardholderName(cardDetails.getCardHolderName())
                            .withExpiryDate(cardDetails.getExpiryDate())
                            .withAddressLine1(cardDetails.getBillingAddress().map(AddressEntity::getLine1).orElse(null))
                            .withAddressLine2(cardDetails.getBillingAddress().map(AddressEntity::getLine2).orElse(null))
                            .withAddressCity(cardDetails.getBillingAddress().map(AddressEntity::getCity).orElse(null))
                            .withAddressCountry(cardDetails.getBillingAddress().map(AddressEntity::getCountry).orElse(null))
                            .withAddressCounty(cardDetails.getBillingAddress().map(AddressEntity::getCounty).orElse(null))
                            .withAddressPostcode(cardDetails.getBillingAddress().map(AddressEntity::getPostcode).orElse(null)));
                
        return builder.build();
    }

    public Long getCorporateSurcharge() {
        return corporateSurcharge;
    }

    public String getEmail() {
        return email;
    }

    public String getCardBrand() {
        return cardBrand;
    }
    
    public String getCardBrandLabel() { 
        return cardBrandLabel; 
    }

    public String getFirstDigitsCardNumber() {
        return firstDigitsCardNumber;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getAddressPostcode() {
        return addressPostcode;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public String getAddressCounty() {
        return addressCounty;
    }

    public String getAddressCountry() {
        return addressCountry;
    }

    public String getWallet() {
        return wallet;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentDetailsEnteredEventDetails that = (PaymentDetailsEnteredEventDetails) o;
        return Objects.equals(corporateSurcharge, that.corporateSurcharge) &&
                Objects.equals(email, that.email) &&
                Objects.equals(cardBrand, that.cardBrand) &&
                Objects.equals(firstDigitsCardNumber, that.firstDigitsCardNumber) &&
                Objects.equals(lastDigitsCardNumber, that.lastDigitsCardNumber) &&
                Objects.equals(gatewayTransactionId, that.gatewayTransactionId) &&
                Objects.equals(cardholderName, that.cardholderName) &&
                Objects.equals(expiryDate, that.expiryDate) &&
                Objects.equals(addressLine1, that.addressLine1) &&
                Objects.equals(addressLine2, that.addressLine2) &&
                Objects.equals(addressPostcode, that.addressPostcode) &&
                Objects.equals(addressCity, that.addressCity) &&
                Objects.equals(addressCounty, that.addressCounty) &&
                Objects.equals(addressCountry, that.addressCountry) &&
                Objects.equals(wallet, that.wallet) &&
                Objects.equals(totalAmount, that.totalAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(corporateSurcharge, email, cardBrand, firstDigitsCardNumber, lastDigitsCardNumber,
                gatewayTransactionId, cardholderName, expiryDate, addressLine1, addressLine2, addressPostcode,
                addressCounty, addressCountry, wallet, totalAmount);
    }

    private static class Builder {
        private Long corporateSurcharge;
        private String email;
        private String cardBrand;
        private String cardBrandLabel;
        private String gatewayTransactionId;
        private String firstDigitsCardNumber;
        private String lastDigitsCardNumber;
        private String cardholderName;
        private String expiryDate;
        private String addressLine1;
        private String addressLine2;
        private String addressPostcode;
        private String addressCity;
        private String addressCounty;
        private String addressCountry;
        private String wallet;
        private Long totalAmount;

        Builder withCorporateSurcharge(Long corporateSurcharge) {
            this.corporateSurcharge = corporateSurcharge;
            return this;
        }

        Builder withEmail(String email) {
            this.email = email;
            return this;
        }

        Builder withCardBrand(String cardBrand) {
            this.cardBrand = cardBrand;
            return this;
        }
        
        Builder withCardBrandLabel(String cardBrandLabel) { 
            this.cardBrandLabel = cardBrandLabel;
            return this;
        }

        Builder withGatewayTransactionId(String gatewayTransactionId) {
            this.gatewayTransactionId = gatewayTransactionId;
            return this;
        }

        Builder withFirstDigitsCardNumber(String firstDigitsCardNumber) {
            this.firstDigitsCardNumber = firstDigitsCardNumber;
            return this;
        }

        Builder withLastDigitsCardNumber(String lastDigitsCardNumber) {
            this.lastDigitsCardNumber = lastDigitsCardNumber;
            return this;
        }

        Builder withCardholderName(String cardholderName) {
            this.cardholderName = cardholderName;
            return this;
        }

        Builder withExpiryDate(String expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        Builder withAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
            return this;
        }

        Builder withAddressLine2(String addressLine2) {
            this.addressLine2 = addressLine2;
            return this;
        }

        Builder withAddressPostcode(String addressPostcode) {
            this.addressPostcode = addressPostcode;
            return this;
        }

        Builder withAddressCity(String addressCity) {
            this.addressCity = addressCity;
            return this;
        }

        Builder withAddressCounty(String addressCounty) {
            this.addressCounty = addressCounty;
            return this;
        }

        Builder withAddressCountry(String addressCountry) {
            this.addressCountry = addressCountry;
            return this;
        }

        Builder withWallet(String wallet) {
            this.wallet = wallet;
            return this;
        }

        Builder withTotalAmount(Long totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        PaymentDetailsEnteredEventDetails build() {
            return new PaymentDetailsEnteredEventDetails(corporateSurcharge, email, cardBrand, cardBrandLabel, gatewayTransactionId, firstDigitsCardNumber, lastDigitsCardNumber, cardholderName, expiryDate, addressLine1, addressLine2, addressPostcode, addressCity, addressCounty, addressCountry, wallet, totalAmount);
        }
    }
}
