package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeGooglePayAuthRequest implements GooglePayAuthRequest {


    @Schema(hidden = true)
    @NotNull
    @Valid
    private final GooglePayPaymentInfo paymentInfo;

    @Schema(hidden = true)
    @NotNull
    @Valid
    private final String tokenId;

    public StripeGooglePayAuthRequest(@JsonProperty("payment_info") GooglePayPaymentInfo paymentInfo, @JsonProperty("token_id") String tokenId) {
        this.paymentInfo = paymentInfo;
        this.tokenId = tokenId;
    }

    @Override
    public GooglePayPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }
    
    public String getTokenId() { return tokenId; }
    
    @Override
    @Schema(hidden = true)
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
}