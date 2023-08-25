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
    private String lastDigitsCardNumber;
    @Schema(example = "visa")
    private String brand;
    @Schema(example = "DEBIT")
    private PayersCardType cardType;
    @Length(max = 255, message = "Card holder name must be a maximum of 255 chars")
    @Schema(example = "Joe B", maxLength = 255)
    private String cardholderName;
    @Length(max = 254, message = "Email must be a maximum of 254 chars")
    @Schema(example = "mr@payment.test", maxLength = 254)
    private String email;
    @Schema(example = "text/html;q=1.0, */*;q=0.9")
    private String acceptHeader;
    @Schema(example = "Mozilla/5.0")
    private String userAgentHeader;
    @Schema(example = "203.0.113.1")
    private String ipAddress;
    @Schema(example = "MasterCard 1234")
    private String displayName;
    @Schema(example = "MasterCard")
    private String network;
    @Schema(example = "372C3858122B6BC39C6095ECA2F994A8AA012F3B025D0D72ECFD449C2A5877F9")
    private String transactionIdentifier;
    private String rawPaymentData;

    @Schema(example = "1f1154b7-620d-4654-801b-893b5bb22db1", description = "SessionId returned by Worldpay/CardinalCommerce as part of device data collection. Applicable for Google Pay payments only")
    @JsonProperty("worldpay_3ds_flex_ddc_result")
    private String worldpay3dsFlexDdcResult;

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

    public String getDisplayName() {
        return displayName;
    }

    public String getNetwork() {
        return network;
    }

    public String getTransactionIdentifier() {
        return transactionIdentifier;
    }

    public String getRawPaymentData() {
        return rawPaymentData;
    }

    public Optional<String> getWorldpay3dsFlexDdcResult() {
        return Optional.ofNullable(worldpay3dsFlexDdcResult);
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
                ", worldpay3dsFlexDdcResult=" + getWorldpay3dsFlexDdcResult().map(x -> "present").orElse("not present") +
                '}';
    }
}
