package uk.gov.pay.connector.events.eventdetails.charge;

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

    public PaymentDetailsEnteredEventDetails(Long corporateSurcharge, String email, String cardBrand,
                                             String gatewayTransactionId, String firstDigitsCardNumber,
                                             String lastDigitsCardNumber, String cardholderName, String expiryDate,
                                             String addressLine1, String addressLine2, String addressPostcode,
                                             String addressCity, String addressCounty, String addressCountry,
                                             String wallet, Long totalAmount) {

        this.corporateSurcharge = corporateSurcharge;
        this.email = email;
        this.cardBrand = cardBrand;
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
        return new PaymentDetailsEnteredEventDetails(
                charge.getCorporateSurcharge().orElse(null),
                charge.getEmail(),
                charge.getCardDetails().getCardBrand(),
                charge.getGatewayTransactionId(),
                Optional.ofNullable(charge.getCardDetails().getFirstDigitsCardNumber()).map(FirstDigitsCardNumber::toString).orElse(null),
                Optional.ofNullable(charge.getCardDetails().getLastDigitsCardNumber())
                        .map(LastDigitsCardNumber::toString)
                        .orElse(null),
                charge.getCardDetails().getCardHolderName(),
                charge.getCardDetails().getExpiryDate(),
                charge.getCardDetails().getBillingAddress().map(a -> a.getLine1()).orElse(null),
                charge.getCardDetails().getBillingAddress().map(a -> a.getLine2()).orElse(null),
                charge.getCardDetails().getBillingAddress().map(a -> a.getPostcode()).orElse(null),
                charge.getCardDetails().getBillingAddress().map(a -> a.getCity()).orElse(null),
                charge.getCardDetails().getBillingAddress().map(a -> a.getCounty()).orElse(null),
                charge.getCardDetails().getBillingAddress().map(a -> a.getCountry()).orElse(null),
                Optional.ofNullable(charge.getWalletType()).map(Enum::toString).orElse(null),
                CorporateCardSurchargeCalculator.getTotalAmountFor(charge));
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
}
