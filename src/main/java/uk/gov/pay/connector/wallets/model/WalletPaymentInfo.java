package uk.gov.pay.connector.wallets.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.gateway.model.PayersCardType;

import java.util.Optional;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletPaymentInfo {
    
    private String lastDigitsCardNumber;
    private String brand;
    private PayersCardType cardType;
    @Length(max = 255, message = "Card holder name must be a maximum of 255 chars") 
    private String cardholderName;
    @Length(max = 254, message = "Email must be a maximum of 254 chars")
    private String email;
    private String acceptHeader;
    private String userAgentHeader;
    private String ipAddress;

    public WalletPaymentInfo() {
    }

    public WalletPaymentInfo(String lastDigitsCardNumber, String brand, PayersCardType cardType, String cardholderName, String email) {
        this.lastDigitsCardNumber = lastDigitsCardNumber;
        this.brand = brand;
        this.cardType = cardType;
        this.cardholderName = cardholderName;
        this.email = email;
    }

    public WalletPaymentInfo(String lastDigitsCardNumber, 
                             String brand,
                             PayersCardType cardType, 
                             String cardholderName,
                             String email,
                             String acceptHeader, 
                             String userAgentHeader, 
                             String ipAddress) {
        this(lastDigitsCardNumber, brand, cardType, cardholderName, email);
        this.acceptHeader = acceptHeader;
        this.userAgentHeader = userAgentHeader;
        this.ipAddress = ipAddress;
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

    public String getAcceptHeader() {
        return acceptHeader;
    }

    public String getUserAgentHeader() {
        return userAgentHeader;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    @Override
    public String toString() { //this might be logged, so we serialise without PII
        return "WalletPaymentInfo{" +
                "lastDigitsCardNumber='" + lastDigitsCardNumber + '\'' +
                ", brand='" + brand + '\'' +
                ", cardType=" + cardType +
                ", acceptHeader=" + acceptHeader +
                ", userAgentHeader=" + userAgentHeader +
                ", ipAddress=" + Optional.ofNullable(ipAddress).map(x -> "ipAddress is present").orElse("ipAddress is not present") +
                '}';
    }
}
