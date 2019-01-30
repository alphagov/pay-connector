package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Optional;

public class GooglePayAuthRequest implements WalletAuthorisationRequest, WalletAuthorisationData {

    @NotNull @Valid private final WalletPaymentInfo paymentInfo;
    @NotNull @Valid private final EncryptedPaymentData encryptedPaymentData;

    GooglePayAuthRequest(@JsonProperty("payment_info") WalletPaymentInfo paymentInfo,
                         @JsonProperty("encrypted_payment_data") EncryptedPaymentData encryptedPaymentData) {
        this.paymentInfo = paymentInfo;
        this.encryptedPaymentData = encryptedPaymentData;
    }

    public WalletPaymentInfo getPaymentInfo() {
        return paymentInfo;
    }

    @Override
    public Optional<LocalDate> getCardExpiryDate() {
        return Optional.empty();
    }

    public EncryptedPaymentData getEncryptedPaymentData() {
        return encryptedPaymentData;
    }

    @Override
    public String getLastDigitsCardNumber() {
        return getPaymentInfo().getLastDigitsCardNumber();
    }

    @Override
    public WalletType getWalletType() {
        return WalletType.GOOGLE_PAY;
    }
}
