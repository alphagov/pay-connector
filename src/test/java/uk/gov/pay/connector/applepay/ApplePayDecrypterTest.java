package uk.gov.pay.connector.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ApplePayConfig;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.applepay.api.AppleCardExpiryDate;
import uk.gov.pay.connector.applepay.api.ApplePayToken;
import uk.gov.pay.connector.applepay.api.PaymentInfo;

import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplePayDecrypterTest {

    @Mock
    private ConnectorConfiguration mockConfig;
    @Mock
    private WorldpayConfig mockWorldpayConfig;
    @Mock
    private ApplePayConfig mockApplePayConfig;
    private ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENCODED_PRIVATE_KEY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgOqukXQoQVbg1xvvq/IGLdK0UuJPbbawULTALcuw/Uz2hRANCAAQPjiA1kTEodST2wy5d5kQFrM0D5qBX9Ukry8W6D+vC7OqbMoTm/upRM1GRHeA2LaVTrwAnpGhoO0ETqYF2Nu4V";

    private static final String ENCODED_PUBLIC_CERTIFICATE = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUVjRENDQkJhZ0F3SUJBZ0lJVXlyRU00SXpCSFF3Q2dZSUtvWkl6ajBFQXdJd2dZQXhOREF5QmdOVkJBTU0KSzBGd2NHeGxJRmR2Y214a2QybGtaU0JFWlhabGJHOXdaWElnVW1Wc1lYUnBiMjV6SUVOQklDMGdSekl4SmpBawpCZ05WQkFzTUhVRndjR3hsSUVObGNuUnBabWxqWVhScGIyNGdRWFYwYUc5eWFYUjVNUk13RVFZRFZRUUtEQXBCCmNIQnNaU0JKYm1NdU1Rc3dDUVlEVlFRR0V3SlZVekFlRncweE5ERXdNall4TWpFd01UQmFGdzB4TmpFeE1qUXgKTWpFd01UQmFNSUdoTVM0d0xBWUtDWkltaVpQeUxHUUJBUXdlYldWeVkyaGhiblF1WTI5dExuTmxZWFJuWldWcgpMbE5sWVhSSFpXVnJNVFF3TWdZRFZRUUREQ3ROWlhKamFHRnVkQ0JKUkRvZ2JXVnlZMmhoYm5RdVkyOXRMbk5sCllYUm5aV1ZyTGxObFlYUkhaV1ZyTVJNd0VRWURWUVFMREFvNVFqTlJXVGxYUWxvMU1SY3dGUVlEVlFRS0RBNVQKWldGMFIyVmxheXdnU1c1akxqRUxNQWtHQTFVRUJoTUNWVk13V1RBVEJnY3Foa2pPUFFJQkJnZ3Foa2pPUFFNQgpCd05DQUFRUGppQTFrVEVvZFNUMnd5NWQ1a1FGck0wRDVxQlg5VWtyeThXNkQrdkM3T3FiTW9UbS91cFJNMUdSCkhlQTJMYVZUcndBbnBHaG9PMEVUcVlGMk51NFZvNElDVlRDQ0FsRXdSd1lJS3dZQkJRVUhBUUVFT3pBNU1EY0cKQ0NzR0FRVUZCekFCaGl0b2RIUndPaTh2YjJOemNDNWhjSEJzWlM1amIyMHZiMk56Y0RBMExXRndjR3hsZDNkawpjbU5oTWpBeE1CMEdBMVVkRGdRV0JCUVdHZktnUGdWQlg4Sk92ODRxMWMwNEhTaE1tekFNQmdOVkhSTUJBZjhFCkFqQUFNQjhHQTFVZEl3UVlNQmFBRklTMmhNdzZobUp5RmxtVTZCcWp2VWpmT3Q4TE1JSUJIUVlEVlIwZ0JJSUIKRkRDQ0FSQXdnZ0VNQmdrcWhraUc5Mk5rQlFFd2dmNHdnY01HQ0NzR0FRVUZCd0lDTUlHMkRJR3pVbVZzYVdGdQpZMlVnYjI0Z2RHaHBjeUJqWlhKMGFXWnBZMkYwWlNCaWVTQmhibmtnY0dGeWRIa2dZWE56ZFcxbGN5QmhZMk5sCmNIUmhibU5sSUc5bUlIUm9aU0IwYUdWdUlHRndjR3hwWTJGaWJHVWdjM1JoYm1SaGNtUWdkR1Z5YlhNZ1lXNWsKSUdOdmJtUnBkR2x2Ym5NZ2IyWWdkWE5sTENCalpYSjBhV1pwWTJGMFpTQndiMnhwWTNrZ1lXNWtJR05sY25ScApabWxqWVhScGIyNGdjSEpoWTNScFkyVWdjM1JoZEdWdFpXNTBjeTR3TmdZSUt3WUJCUVVIQWdFV0ttaDBkSEE2Ckx5OTNkM2N1WVhCd2JHVXVZMjl0TDJObGNuUnBabWxqWVhSbFlYVjBhRzl5YVhSNUx6QTJCZ05WSFI4RUx6QXQKTUN1Z0thQW5oaVZvZEhSd09pOHZZM0pzTG1Gd2NHeGxMbU52YlM5aGNIQnNaWGQzWkhKallUSXVZM0pzTUE0RwpBMVVkRHdFQi93UUVBd0lES0RCUEJna3Foa2lHOTJOa0JpQUVRZ3hBUmprek9FWTBOalU0UTBFeVF6RkRPVU16Ck9FSTRSRVpEUWpWRVFrSXlRVEl5TkRVMk1EZEVSRVV5UmpFeE5EWXlNRVU0TkRZNFJVWTFNa1F5TURoRFFUQUsKQmdncWhrak9QUVFEQWdOSUFEQkZBaUIrUTR6enBNajJESlRDSWhERkJjbXdLMXpRQUM3MGZZMklzWWQ4K054dQp1d0loQUtqOVJyVE95aWFRbm9UNU1xaTNVSG9wYjZ4VHVnbDNMVURCbG9yYUJIeVAKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==";
    private ApplePayToken applePayToken;

    private ApplePayDecrypter applePayDecrypter;

    @Before
    public void setUp() throws IOException {
        ApplePayToken.EncryptedPaymentData encryptedPaymentData = objectMapper.readValue(fixture("applepay/token.json"), ApplePayToken.EncryptedPaymentData.class);
        applePayToken = new ApplePayToken(
                new PaymentInfo(),
                encryptedPaymentData
        );
        when(mockConfig.getWorldpayConfig()).thenReturn(mockWorldpayConfig);
        when(mockWorldpayConfig.getApplePayConfig()).thenReturn(mockApplePayConfig);
        when(mockApplePayConfig.getPrivateKey()).thenReturn(Base64.decode(ENCODED_PRIVATE_KEY));
        when(mockApplePayConfig.getPublicCertificate()).thenReturn(Base64.decode(ENCODED_PUBLIC_CERTIFICATE));
        applePayDecrypter = new ApplePayDecrypter(mockConfig, objectMapper);
    }

    @Test
    public void shouldDecryptData_whenPrivateKeyAndPublicCertificateAreValid() {
        AppleDecryptedPaymentData appleDecryptedPaymentData = applePayDecrypter.performDecryptOperation(applePayToken);
        assertThat(appleDecryptedPaymentData.getApplicationExpirationDate(), is(new AppleCardExpiryDate("200731")));
        assertThat(appleDecryptedPaymentData.getApplicationPrimaryAccountNumber(), is("4109370251004320"));
        assertThat(appleDecryptedPaymentData.getCurrencyCode(), is("840"));
        assertThat(appleDecryptedPaymentData.getDeviceManufacturerIdentifier(), is("040010030273"));
        assertThat(appleDecryptedPaymentData.getTransactionAmount(), is("100"));
        assertThat(appleDecryptedPaymentData.getPaymentDataType(), is("3DSecure"));
        assertThat(appleDecryptedPaymentData.getPaymentData().getOnlinePaymentCryptogram(), is("Af9x/QwAA/DjmU65oyc1MAABAAA="));
        assertThat(appleDecryptedPaymentData.getPaymentData().getEciIndicator(), is("5"));
    }

    @Test(expected = InvalidKeyException.class)
    public void shouldThrowException_whenPublicCertificateIsInvalid() {
        when(mockApplePayConfig.getPublicCertificate()).thenReturn("nope".getBytes(UTF_8));
        applePayDecrypter = new ApplePayDecrypter(mockConfig, objectMapper);
        applePayDecrypter.performDecryptOperation(applePayToken);
    }

    @Test(expected = InvalidKeyException.class)
    public void shouldThrowException_whenPrivateKeyIsInvalid() {
        when(mockApplePayConfig.getPrivateKey()).thenReturn("nope".getBytes(UTF_8));
        applePayDecrypter = new ApplePayDecrypter(mockConfig, objectMapper);
        applePayDecrypter.performDecryptOperation(applePayToken);
    }
}
