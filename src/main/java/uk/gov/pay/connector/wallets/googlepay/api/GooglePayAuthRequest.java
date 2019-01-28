package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletTemplateData;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

public class GooglePayAuthRequest implements WalletAuthorisationRequest, WalletTemplateData, WalletAuthorisationData {

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
    public WalletTemplateData getWalletTemplateData() {
        return this;
    }

    @Override
    public LocalDate getCardExpiryDate() {
        //TODO card expiry data is required when creating authCardDetails but not supplied by Google.
        return LocalDate.now().plusYears(2);
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
