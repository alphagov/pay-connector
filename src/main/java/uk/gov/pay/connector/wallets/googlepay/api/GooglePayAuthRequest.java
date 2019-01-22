package uk.gov.pay.connector.wallets.googlepay.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class GooglePayAuthRequest {

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
}
