package uk.gov.pay.connector.webpayments.googlepay;

import com.google.crypto.tink.apps.paymentmethodtoken.GooglePaymentsPublicKeysManager;
import org.junit.Ignore;
import org.junit.Test;
import uk.gov.pay.connector.webpayments.PaymentData;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@Ignore
public class GooglePayDecrypterTest {
    
    // this is got by performing the following on the resources/googlepay/test-private-key.pem:
    // openssl pkcs8 -topk8 -inform PEM -outform DER -in test-private-key.pem  -nocrypt | base64 | paste -sd "\0" -
    private static final String PRIVATE_KEY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgJeFYEqqxGkLmJsBEm8AoKqvMMk2jVnAxJsUv6Ur1Hw6hRANCAAS8s/nVkI04fve0Yk+2zrZEpL3BGTm1IAsWp7XVIy5EALRlnHDEPp0Kegmk2oSwTcVve9ennSmiXBBvePNkjJMF";
    
    @Test
    public void decryptSuccessfully() throws Exception {
        GooglePayDecrypter googlePayDecrypter = new GooglePayDecrypter();
        GooglePaymentsPublicKeysManager.INSTANCE_TEST.refreshInBackground();
        PaymentData paymentData = googlePayDecrypter.decrypt(load("googlepay/google-pay-response.json"), PRIVATE_KEY, false, "12345678901234567890");
        assertThat(paymentData.worldpayTokenNumber).isEqualTo("4111111111111111");
        assertThat(paymentData.eciIndicator.isPresent()).isFalse();
        assertThat(paymentData.onlinePaymentCryptogram.isPresent()).isFalse();
    }
}
