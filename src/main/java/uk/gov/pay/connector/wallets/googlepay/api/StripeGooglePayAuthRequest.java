package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.time.LocalDate;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeGooglePayAuthRequest implements WalletAuthorisationData {
    
    private final WalletPaymentInfo paymentInfo;
    private final String tokenId;

    StripeGooglePayAuthRequest(@JsonProperty("payment_info") WalletPaymentInfo paymentInfo,
                               @JsonProperty("token_id") String tokenId) {
        this.paymentInfo = paymentInfo;
        this.tokenId = tokenId;
    }

    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Override
    public Optional<LocalDate> getCardExpiryDate() {
        return Optional.empty();
    }

    @Override
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }

    @Override
    public String getLastDigitsCardNumber() {
        return paymentInfo.getLastDigitsCardNumber();
    }

    public String getTokenId() {
        return tokenId;
    }
}
