package uk.gov.pay.connector.wallets.googlepay;

import com.google.crypto.tink.apps.paymentmethodtoken.GooglePaymentsPublicKeysManager;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.wallets.PaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.EncryptedPaymentData;

import static org.assertj.core.api.Java6Assertions.assertThat;

@Ignore
public class GooglePayDecrypterTest {
    
    // this is got by performing the following on the resources/googlepay/test-private-key.pem:
    // openssl pkcs8 -topk8 -inform PEM -outform DER -in test-private-key.pem  -nocrypt | base64 | paste -sd "\0" -
    private static final String PRIVATE_KEY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgJeFYEqqxGkLmJsBEm8AoKqvMMk2jVnAxJsUv6Ur1Hw6hRANCAAS8s/nVkI04fve0Yk+2zrZEpL3BGTm1IAsWp7XVIy5EALRlnHDEPp0Kegmk2oSwTcVve9ennSmiXBBvePNkjJMF";
    
    @Test
    public void decryptPAN_ONLY() throws Exception {
        GooglePaymentsPublicKeysManager.INSTANCE_TEST.refreshInBackground();
        EncryptedPaymentData encryptedPaymentData = new EncryptedPaymentData(null, null, null, "DIRECT", "ECv2");
        PaymentData paymentData = new GooglePayDecrypter().decrypt(encryptedPaymentData, PRIVATE_KEY, false, "12345678901234567890");
        assertThat(paymentData.worldpayTokenNumber).isEqualTo("??");
        assertThat(paymentData.eciIndicator.isPresent()).isFalse();
        assertThat(paymentData.onlinePaymentCryptogram.isPresent()).isFalse();
    }
    
    @Test
    public void decryptCRYPTOGRAM_3DS() {
        
    }
}
