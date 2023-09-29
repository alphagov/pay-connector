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
public class WorldpayGooglePayAuthRequest implements GooglePayAuthRequest {

    @Schema(hidden = true)
    @NotNull
    @Valid
    private final WalletPaymentInfo paymentInfo;
    @Schema(hidden = true)
    @NotNull
    @Valid
    private final GooglePayEncryptedPaymentData encryptedPaymentData;

    public WorldpayGooglePayAuthRequest(@JsonProperty("payment_info") WalletPaymentInfo paymentInfo,
                                 @JsonProperty("encrypted_payment_data") GooglePayEncryptedPaymentData encryptedPaymentData) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    public GooglePayEncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    @Override
    @Schema(hidden = true)
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
}
