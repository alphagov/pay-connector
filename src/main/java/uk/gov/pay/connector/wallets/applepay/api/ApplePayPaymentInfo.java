package uk.gov.pay.connector.wallets.applepay.api;

import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

public class ApplePayPaymentInfo extends WalletPaymentInfo {
    
    @Schema(example = "MasterCard 1234")
    private String displayName;
    
    @Schema(example = "MasterCard")
    private String network;
    
    @Schema(example = "372C3858122B6BC39C6095ECA2F994A8AA012F3B025D0D72ECFD449C2A5877F9")
    private String transactionIdentifier;

    public ApplePayPaymentInfo() {
    }
    
    public ApplePayPaymentInfo(String lastDigitsCardNumber,
                               String brand,
                               PayersCardType cardType,
                               String cardholderName,
                               String email,
                               String displayName,
                               String network,
                               String transactionIdentifier) {
        super(lastDigitsCardNumber, brand, cardType, cardholderName, email);
        this.displayName = displayName;
        this.network = network;
        this.transactionIdentifier = transactionIdentifier;
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

    @Override
    public String toString() { // this might be logged, so we serialise without PII
        return "ApplePayPaymentInfo{" +
                "displayName='" + displayName + '\'' +
                ", network='" + network + '\'' +
                ", transactionIdentifier='" + transactionIdentifier + '\'' +
                ", lastDigitsCardNumber='" + lastDigitsCardNumber + '\'' +
                ", brand='" + brand + '\'' +
                ", cardType=" + cardType +
                '}';
    }
}
