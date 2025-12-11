package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;

import java.util.Optional;

import static io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY;

public class GooglePayAuthRequest implements WalletAuthorisationRequest {

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

    public GooglePayAuthRequest(@JsonProperty("payment_info") GooglePayPaymentInfo paymentInfo,
                                @JsonProperty("encrypted_payment_data") GooglePayEncryptedPaymentData encryptedPaymentData,
                                @JsonProperty("token_id") String tokenId) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
        this.tokenId = tokenId;
    }

    public GooglePayAuthRequest(GooglePayPaymentInfo paymentInfo,
                                GooglePayEncryptedPaymentData encryptedPaymentData) {
        this(paymentInfo, encryptedPaymentData, null);
    }

    public GooglePayAuthRequest(GooglePayPaymentInfo paymentInfo,
                                String tokenId) {
        this(paymentInfo, null, tokenId);
    }

    @Override
    public GooglePayPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Schema(description = "only required for Stripe payments", accessMode = WRITE_ONLY)
    public Optional<String> getTokenId() { return Optional.of(tokenId); }

    public Optional<GooglePayEncryptedPaymentData> getEncryptedPaymentData() {
        return Optional.of(encryptedPaymentData);
    }

    @Schema(hidden = true)
    @Override
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
}
