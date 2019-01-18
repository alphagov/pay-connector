package uk.gov.pay.connector.wallets.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import uk.gov.pay.connector.gateway.model.PayersCardType;

@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class WalletPaymentInfo {
    
    private String lastDigitsCardNumber;
    private String brand;
    private PayersCardType cardType;
    private String cardholderName;
    private String email;

    public WalletPaymentInfo() {
    }

    public WalletPaymentInfo(String lastDigitsCardNumber, String brand, PayersCardType cardType, String cardholderName, String email) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.brand = brand;
        this.cardType = cardType;
        this.cardholderName = cardholderName;
        this.email = email;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getEmail() {
        return email;
    }

    public String getLastDigitsCardNumber() {
        return lastDigitsCardNumber;
    }

    public String getBrand() {
        return brand;
    }

    public PayersCardType getCardType() {
        return cardType;
    }

    @Override
    public String toString() { //this might be logged, so we serialise without PII
        return "WalletPaymentInfo{" +
                "lastDigitsCardNumber='" + lastDigitsCardNumber + '\'' +
                ", brand='" + brand + '\'' +
                ", cardType=" + cardType +
                '}';
    }
}
