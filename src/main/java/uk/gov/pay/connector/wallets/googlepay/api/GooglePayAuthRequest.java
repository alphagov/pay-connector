package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;
import uk.gov.pay.connector.wallets.model.WalletTemplateData;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GooglePayAuthRequest implements WalletTemplateData {

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
