package uk.gov.pay.connector.wallets.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;
import uk.gov.pay.connector.gateway.model.PayersCardType;

import java.util.Optional;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletPaymentInfo {

    @Schema(description = "last digits card number", example = "4242")
    protected String lastDigitsCardNumber;
    @Schema(example = "visa")
    protected String brand;
    @Schema(example = "DEBIT")
    protected PayersCardType cardType;
    @Length(max = 255, message = "Card holder name must be a maximum of 255 chars")
    @Schema(example = "Joe B", maxLength = 255)
    protected String cardholderName;
    @Length(max = 254, message = "Email must be a maximum of 254 chars")
    @Schema(example = "mr@payment.test", maxLength = 254)
    protected String email;

    public WalletPaymentInfo() {
    }

    public WalletPaymentInfo(String lastDigitsCardNumber,
                             String brand,
                             PayersCardType cardType,
                             String cardholderName,
                             String email) {
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
    public String toString() { // this might be logged, so we serialise without PII
        return "WalletPaymentInfo{" +
                "lastDigitsCardNumber='" + lastDigitsCardNumber + '\'' +
                ", brand='" + brand + '\'' +
                ", cardType=" + cardType +
                '}';
    }
}
