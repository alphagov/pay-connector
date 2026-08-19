package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;

public record AdyenGooglePayAuthRequest(
        @Schema(name = "payment_info", implementation = GooglePayPaymentInfo.class)
        @NotNull
        @Valid
        @JsonProperty("payment_info")
        GooglePayPaymentInfo paymentInfo,

        @NotNull
        @Valid
        @JsonProperty("token") 
        String token
) implements WalletAuthorisationRequest {

    @Override
    public GooglePayPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Schema(hidden = true)
    @Override
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[paymentInfo=" + paymentInfo + ", token=redacted]";
    }

}
