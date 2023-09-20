package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldpayGooglePayAuthRequest implements WalletAuthorisationRequest, WalletAuthorisationData {

    @Schema(hidden = true)
    @NotNull
    @Valid
    private final WalletPaymentInfo paymentInfo;
    @Schema(hidden = true)
    @NotNull
    @Valid
    private final EncryptedPaymentData encryptedPaymentData;

    WorldpayGooglePayAuthRequest(@JsonProperty("payment_info") WalletPaymentInfo paymentInfo,
                                 @JsonProperty("encrypted_payment_data") EncryptedPaymentData encryptedPaymentData) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Override
    @Schema(hidden = true)
    public Optional<LocalDate> getCardExpiryDate() {
        return Optional.empty();
    }

    public EncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    @Override
    @Schema(hidden = true)
    public String getLastDigitsCardNumber() {
        return getPaymentInfo().getLastDigitsCardNumber();
    }

    @Override
    @Schema(hidden = true)
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
}
