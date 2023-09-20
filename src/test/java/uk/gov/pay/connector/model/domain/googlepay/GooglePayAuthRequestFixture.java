package uk.gov.pay.connector.model.domain.googlepay;

import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.wallets.WalletAuthorisationRequest;
import uk.gov.pay.connector.wallets.WalletType;
import uk.gov.pay.connector.wallets.googlepay.api.EncryptedPaymentData;
import uk.gov.pay.connector.wallets.model.WalletAuthorisationData;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.time.LocalDate;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.applepay.WalletPaymentInfoFixture.aWalletPaymentInfo;

public class GooglePayAuthRequestFixture implements WalletAuthorisationRequest, WalletAuthorisationData {
    
    private WalletPaymentInfo paymentInfo = aWalletPaymentInfo().build();
    
    private EncryptedPaymentData encryptedPaymentData = new EncryptedPaymentData(
            "aSignedMessage",
            "ECv1",
            "MEYCIQC+a+AzSpQGr42UR1uTNX91DQM2r7SeKwzNs0UPoeSrrQIhAPpSzHjYTvvJGGzWwli8NRyHYE/diQMLL8aXqm9VIrwl"
    );

    private GooglePayAuthRequestFixture() {
    }
    
    public static GooglePayAuthRequestFixture anGooglePayAuthRequestFixture() {
        return new GooglePayAuthRequestFixture();
        
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
    
    public GooglePayAuthRequestFixture withGooglePaymentInfo(WalletPaymentInfo googlePaymentInfo) {
        this.paymentInfo = googlePaymentInfo;
        return this;
    }

    public GooglePayAuthRequestFixture withEncryptedCardData(EncryptedPaymentData encryptedCardData) {
        this.encryptedPaymentData = encryptedCardData;
        return this;
    }
    
    public GooglePayAuthRequestFixture build() {
        return this;
    }
}
