package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.wallets.WalletType;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GenericGooglePayAuthRequest {
    
    @Schema(hidden = true)
    @NotNull
    @Valid
    private final GooglePayPaymentInfo paymentInfo;
    
    @Valid
    @Schema(hidden = true)
    private final GooglePayEncryptedPaymentData encryptedPaymentData;
    
    @Valid
    @JsonProperty("token_id")
    private final String tokenId;

    public GenericGooglePayAuthRequest(@JsonProperty("payment_info") GooglePayPaymentInfo paymentInfo,
                                       @JsonProperty("encrypted_payment_data") GooglePayEncryptedPaymentData encryptedPaymentData,
                                       @JsonProperty("token_id") String tokenId) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
        this.tokenId = tokenId;
    }
    
    public GooglePayPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Schema(description = "only required for Stripe payments", writeOnly = true)
    public String getTokenId() { return tokenId; }
    
    public GooglePayEncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }
    
    @Schema(hidden = true)
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
    
    public WorldpayGooglePayAuthRequest toWorldpayGooglePayAuthRequest() {
        return new WorldpayGooglePayAuthRequest(paymentInfo, encryptedPaymentData);
    }
    
    public StripeGooglePayAuthRequest toStripeGooglePayAuthRequest() {
        return new StripeGooglePayAuthRequest(paymentInfo, tokenId);
    }
}
