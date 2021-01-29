package uk.gov.pay.connector.wallets.applepay;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ApplePayConfig;
import uk.gov.pay.connector.app.WorldpayConfig;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.wallets.applepay.ApplePayAuthRequestBuilder.anApplePayToken;

@ExtendWith(MockitoExtension.class)
class ApplePayDecrypterTest {

    private static final String ENCODED_PRIMARY_PRIVATE_KEY = "MIGHAgEAMBMGB yqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgOqukXQoQVbg1xvvq/IGLdK0UuJPbbawULTALcuw/Uz2hRANCAAQPjiA1kTEodST2wy5d5kQFrM0D5qBX9Ukry8W6D+vC7OqbMoTm/upRM1GRHeA2LaVTrwAnpGhoO0ETqYF2Nu4V";
    private static final String ENCODED_PRIMARY_PUBLIC_CERTIFICATE = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS 0tCk1JSUVjRENDQkJhZ0F3SUJBZ0lJVXlyRU00SXpCSFF3Q2dZSUtvWkl6ajBFQXdJd2dZQXhOREF5QmdOVkJBTU0KSzBGd2NHeGxJRmR2Y214a2QybGtaU0JFWlhabGJHOXdaWElnVW1Wc1lYUnBiMjV6SUVOQklDMGdSekl4SmpBawpCZ05WQkFzTUhVRndjR3hsSUVObGNuUnBabWxqWVhScGIyNGdRWFYwYUc5eWFYUjVNUk13RVFZRFZRUUtEQXBCCmNIQnNaU0JKYm1NdU1Rc3dDUVlEVlFRR0V3SlZVekFlRncweE5ERXdNall4TWpFd01UQmFGdzB4TmpFeE1qUXgKTWpFd01UQmFNSUdoTVM0d0xBWUtDWkltaVpQeUxHUUJBUXdlYldWeVkyaGhiblF1WTI5dExuTmxZWFJuWldWcgpMbE5sWVhSSFpXVnJNVFF3TWdZRFZRUUREQ3ROWlhKamFHRnVkQ0JKUkRvZ2JXVnlZMmhoYm5RdVkyOXRMbk5sCllYUm5aV1ZyTGxObFlYUkhaV1ZyTVJNd0VRWURWUVFMREFvNVFqTlJXVGxYUWxvMU1SY3dGUVlEVlFRS0RBNVQKWldGMFIyVmxheXdnU1c1akxqRUxNQWtHQTFVRUJoTUNWVk13V1RBVEJnY3Foa2pPUFFJQkJnZ3Foa2pPUFFNQgpCd05DQUFRUGppQTFrVEVvZFNUMnd5NWQ1a1FGck0wRDVxQlg5VWtyeThXNkQrdkM3T3FiTW9UbS91cFJNMUdSCkhlQTJMYVZUcndBbnBHaG9PMEVUcVlGMk51NFZvNElDVlRDQ0FsRXdSd1lJS3dZQkJRVUhBUUVFT3pBNU1EY0cKQ0NzR0FRVUZCekFCaGl0b2RIUndPaTh2YjJOemNDNWhjSEJzWlM1amIyMHZiMk56Y0RBMExXRndjR3hsZDNkawpjbU5oTWpBeE1CMEdBMVVkRGdRV0JCUVdHZktnUGdWQlg4Sk92ODRxMWMwNEhTaE1tekFNQmdOVkhSTUJBZjhFCkFqQUFNQjhHQTFVZEl3UVlNQmFBRklTMmhNdzZobUp5RmxtVTZCcWp2VWpmT3Q4TE1JSUJIUVlEVlIwZ0JJSUIKRkRDQ0FSQXdnZ0VNQmdrcWhraUc5Mk5rQlFFd2dmNHdnY01HQ0NzR0FRVUZCd0lDTUlHMkRJR3pVbVZzYVdGdQpZMlVnYjI0Z2RHaHBjeUJqWlhKMGFXWnBZMkYwWlNCaWVTQmhibmtnY0dGeWRIa2dZWE56ZFcxbGN5QmhZMk5sCmNIUmhibU5sSUc5bUlIUm9aU0IwYUdWdUlHRndjR3hwWTJGaWJHVWdjM1JoYm1SaGNtUWdkR1Z5YlhNZ1lXNWsKSUdOdmJtUnBkR2x2Ym5NZ2IyWWdkWE5sTENCalpYSjBhV1pwWTJGMFpTQndiMnhwWTNrZ1lXNWtJR05sY25ScApabWxqWVhScGIyNGdjSEpoWTNScFkyVWdjM1JoZEdWdFpXNTBjeTR3TmdZSUt3WUJCUVVIQWdFV0ttaDBkSEE2Ckx5OTNkM2N1WVhCd2JHVXVZMjl0TDJObGNuUnBabWxqWVhSbFlYVjBhRzl5YVhSNUx6QTJCZ05WSFI4RUx6QXQKTUN1Z0thQW5oaVZvZEhSd09pOHZZM0pzTG1Gd2NHeGxMbU52YlM5aGNIQnNaWGQzWkhKallUSXVZM0pzTUE0RwpBMVVkRHdFQi93UUVBd0lES0RCUEJna3Foa2lHOTJOa0JpQUVRZ3hBUmprek9FWTBOalU0UTBFeVF6RkRPVU16Ck9FSTRSRVpEUWpWRVFrSXlRVEl5TkRVMk1EZEVSRVV5UmpFeE5EWXlNRVU0TkRZNFJVWTFNa1F5TURoRFFUQUsKQmdncWhrak9QUVFEQWdOSUFEQkZBaUIrUTR6enBNajJESlRDSWhERkJjbXdLMXpRQUM3MGZZMklzWWQ4K054dQp1d0loQUtqOVJyVE95aWFRbm9UNU1xaTNVSG9wYjZ4VHVnbDNMVURCbG9yYUJIeVAKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQ==";

    @Mock private WorldpayConfig mockWorldpayConfig;
    @Mock private ApplePayConfig mockApplePayConfig;
    private ObjectMapper objectMapper = new ObjectMapper();
    private ApplePayAuthRequest applePayAuthRequest;
    private ApplePayDecrypter applePayDecrypter;

    @BeforeEach
    void setUp() throws IOException {
        applePayAuthRequest = anApplePayToken().build();
        when(mockWorldpayConfig.getApplePayConfig()).thenReturn(mockApplePayConfig);
        when(mockApplePayConfig.getPrimaryPrivateKey()).thenReturn(ENCODED_PRIMARY_PRIVATE_KEY);
        when(mockApplePayConfig.getPrimaryPublicCertificate()).thenReturn(ENCODED_PRIMARY_PUBLIC_CERTIFICATE);
        when(mockApplePayConfig.getSecondaryPrivateKey()).thenReturn(Optional.empty());
        when(mockApplePayConfig.getSecondaryPublicCertificate()).thenReturn(Optional.empty());
        applePayDecrypter = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);
    }

    @Test
    void should_throw_exception_when_primary_key_and_cert_are_invalid_and_secondary_key_and_cert_are_missing() {
        String invalidPrimaryKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgjyo3fzxT7j+CFxC7I4B5iVee2FUyn2vfOSjcgp2/g6qhRANCAARdoBFEtnuapXFKw4DYWsW0yV4bavpdWKszkefi19AhlIRE3WSNWSn25W5tZNFjMWtLISBmqANyufx2xP19oRvy"; //pragma: allowlist secret
        String invalidPrimaryCert = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUVlVENDQkNDZ0F3SUJBZ0lJVWtJS3NpWXhhS293Q2dZSUtvWkl6ajBFQXdJd2dZRXhPekE1QmdOVkJBTU1NbFJsYzNRZ1FYQndiR1VnVjI5eWJHUjNhV1JsSUVSbGRtVnNiM0JsY25NZ1VtVnNZWFJwYjI1eklFTkJJQzBnUlVORE1TQXdIZ1lEVlFRTERCZERaWEowYVdacFkyRjBhVzl1SUVGMWRHaHZjbWwwZVRFVE1CRUdBMVVFQ2d3S1FYQndiR1VnU1c1akxqRUxNQWtHQTFVRUJoTUNWVk13SGhjTk1UWXdOekkxTVRVeU1ETTRXaGNOTVRnd09ESTBNVFV5TURNNFdqQ0JsekVyTUNrR0NnbVNKb21UOGl4a0FRRU1HMjFsY21Ob1lXNTBMbkpsWkhSbFlXMHVkMkZ5YzJGM0xteGliekV4TUM4R0ExVUVBd3dvVFdWeVkyaGhiblFnU1VRNklHMWxjbU5vWVc1MExuSmxaSFJsWVcwdWQyRnljMkYzTG14aWJ6RVRNQkVHQTFVRUN3d0tUVmxVVlRoWk5VUlJUVEVUTUJFR0ExVUVDZ3dLUVhCd2JHVWdTVzVqTGpFTE1Ba0dBMVVFQmhNQ1ZWTXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUmRvQkZFdG51YXBYRkt3NERZV3NXMHlWNGJhdnBkV0tzemtlZmkxOUFobElSRTNXU05XU24yNVc1dFpORmpNV3RMSVNCbXFBTnl1ZngyeFAxOW9SdnlvNElDYURDQ0FtUXdUd1lJS3dZQkJRVUhBUUVFUXpCQk1EOEdDQ3NHQVFVRkJ6QUJoak5vZEhSd09pOHZiMk56Y0MxMVlYUXVZMjl5Y0M1aGNIQnNaUzVqYjIwdmIyTnpjREEwTFhSbGMzUjNkMlJ5WTJGbFkyTXdIUVlEVlIwT0JCWUVGQVY3blM0bU5ETHkxZ3h2T0FjQ1MxaE9nWTRsTUF3R0ExVWRFd0VCL3dRQ01BQXdId1lEVlIwakJCZ3dGb0FVMXRiVld1WC8vY0o4Tk1ORDNyMW9kbHcycWI0d2dnRWRCZ05WSFNBRWdnRVVNSUlCRURDQ0FRd0dDU3FHU0liM1kyUUZBVENCL2pDQnd3WUlLd1lCQlFVSEFnSXdnYllNZ2JOU1pXeHBZVzVqWlNCdmJpQjBhR2x6SUdObGNuUnBabWxqWVhSbElHSjVJR0Z1ZVNCd1lYSjBlU0JoYzNOMWJXVnpJR0ZqWTJWd2RHRnVZMlVnYjJZZ2RHaGxJSFJvWlc0Z1lYQndiR2xqWVdKc1pTQnpkR0Z1WkdGeVpDQjBaWEp0Y3lCaGJtUWdZMjl1WkdsMGFXOXVjeUJ2WmlCMWMyVXNJR05sY25ScFptbGpZWFJsSUhCdmJHbGplU0JoYm1RZ1kyVnlkR2xtYVdOaGRHbHZiaUJ3Y21GamRHbGpaU0J6ZEdGMFpXMWxiblJ6TGpBMkJnZ3JCZ0VGQlFjQ0FSWXFhSFIwY0RvdkwzZDNkeTVoY0hCc1pTNWpiMjB2WTJWeWRHbG1hV05oZEdWaGRYUm9iM0pwZEhrdk1FRUdBMVVkSHdRNk1EZ3dOcUEwb0RLR01HaDBkSEE2THk5amNtd3RkV0YwTG1OdmNuQXVZWEJ3YkdVdVkyOXRMMkZ3Y0d4bGQzZGtjbU5oWldOakxtTnliREFPQmdOVkhROEJBZjhFQkFNQ0F5Z3dUd1lKS29aSWh2ZGpaQVlnQkVJTVFEVTRNREpFTVVNM056UkdNRGsyTWtZNE1URXlORGhGTlRNNFJFVXpRa1ZHTmpnd1F6YzVPRFpDUWpWQ05FUkRSVEJDTlRZeU5EbEdNemREUWtJNU5ETXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdUanRpWWprL0JLcDNWOERnNm1JbGNtNUZDT0YwNnp1YjdKc3I2d0NzdktBQ0lIOFUxMTRESTVIbm1mY052d000UlhGRFBUb29wNCtqak1BUHZpZGlwS2tnCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0="; //pragma: allowlist secret

        when(mockWorldpayConfig.getApplePayConfig()).thenReturn(mockApplePayConfig);
        when(mockApplePayConfig.getPrimaryPrivateKey()).thenReturn(invalidPrimaryKey);
        when(mockApplePayConfig.getPrimaryPublicCertificate()).thenReturn(invalidPrimaryCert);
        var applePayDecrypterWithInvalidPrimaryAndMissingSecondaryKeyCerts = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);

        assertThrows(InvalidKeyException.class, () -> 
                applePayDecrypterWithInvalidPrimaryAndMissingSecondaryKeyCerts.performDecryptOperation(applePayAuthRequest));
    }
    
    @Test
    void should_decrypt_data_with_secondary_keys_when_primary_private_key_and_public_certificate_are_invalid() {
        String invalidPrimaryKey = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgjyo3fzxT7j+CFxC7I4B5iVee2FUyn2vfOSjcgp2/g6qhRANCAARdoBFEtnuapXFKw4DYWsW0yV4bavpdWKszkefi19AhlIRE3WSNWSn25W5tZNFjMWtLISBmqANyufx2xP19oRvy"; //pragma: allowlist secret
        String invalidPrimaryCert = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUVlVENDQkNDZ0F3SUJBZ0lJVWtJS3NpWXhhS293Q2dZSUtvWkl6ajBFQXdJd2dZRXhPekE1QmdOVkJBTU1NbFJsYzNRZ1FYQndiR1VnVjI5eWJHUjNhV1JsSUVSbGRtVnNiM0JsY25NZ1VtVnNZWFJwYjI1eklFTkJJQzBnUlVORE1TQXdIZ1lEVlFRTERCZERaWEowYVdacFkyRjBhVzl1SUVGMWRHaHZjbWwwZVRFVE1CRUdBMVVFQ2d3S1FYQndiR1VnU1c1akxqRUxNQWtHQTFVRUJoTUNWVk13SGhjTk1UWXdOekkxTVRVeU1ETTRXaGNOTVRnd09ESTBNVFV5TURNNFdqQ0JsekVyTUNrR0NnbVNKb21UOGl4a0FRRU1HMjFsY21Ob1lXNTBMbkpsWkhSbFlXMHVkMkZ5YzJGM0xteGliekV4TUM4R0ExVUVBd3dvVFdWeVkyaGhiblFnU1VRNklHMWxjbU5vWVc1MExuSmxaSFJsWVcwdWQyRnljMkYzTG14aWJ6RVRNQkVHQTFVRUN3d0tUVmxVVlRoWk5VUlJUVEVUTUJFR0ExVUVDZ3dLUVhCd2JHVWdTVzVqTGpFTE1Ba0dBMVVFQmhNQ1ZWTXdXVEFUQmdjcWhrak9QUUlCQmdncWhrak9QUU1CQndOQ0FBUmRvQkZFdG51YXBYRkt3NERZV3NXMHlWNGJhdnBkV0tzemtlZmkxOUFobElSRTNXU05XU24yNVc1dFpORmpNV3RMSVNCbXFBTnl1ZngyeFAxOW9SdnlvNElDYURDQ0FtUXdUd1lJS3dZQkJRVUhBUUVFUXpCQk1EOEdDQ3NHQVFVRkJ6QUJoak5vZEhSd09pOHZiMk56Y0MxMVlYUXVZMjl5Y0M1aGNIQnNaUzVqYjIwdmIyTnpjREEwTFhSbGMzUjNkMlJ5WTJGbFkyTXdIUVlEVlIwT0JCWUVGQVY3blM0bU5ETHkxZ3h2T0FjQ1MxaE9nWTRsTUF3R0ExVWRFd0VCL3dRQ01BQXdId1lEVlIwakJCZ3dGb0FVMXRiVld1WC8vY0o4Tk1ORDNyMW9kbHcycWI0d2dnRWRCZ05WSFNBRWdnRVVNSUlCRURDQ0FRd0dDU3FHU0liM1kyUUZBVENCL2pDQnd3WUlLd1lCQlFVSEFnSXdnYllNZ2JOU1pXeHBZVzVqWlNCdmJpQjBhR2x6SUdObGNuUnBabWxqWVhSbElHSjVJR0Z1ZVNCd1lYSjBlU0JoYzNOMWJXVnpJR0ZqWTJWd2RHRnVZMlVnYjJZZ2RHaGxJSFJvWlc0Z1lYQndiR2xqWVdKc1pTQnpkR0Z1WkdGeVpDQjBaWEp0Y3lCaGJtUWdZMjl1WkdsMGFXOXVjeUJ2WmlCMWMyVXNJR05sY25ScFptbGpZWFJsSUhCdmJHbGplU0JoYm1RZ1kyVnlkR2xtYVdOaGRHbHZiaUJ3Y21GamRHbGpaU0J6ZEdGMFpXMWxiblJ6TGpBMkJnZ3JCZ0VGQlFjQ0FSWXFhSFIwY0RvdkwzZDNkeTVoY0hCc1pTNWpiMjB2WTJWeWRHbG1hV05oZEdWaGRYUm9iM0pwZEhrdk1FRUdBMVVkSHdRNk1EZ3dOcUEwb0RLR01HaDBkSEE2THk5amNtd3RkV0YwTG1OdmNuQXVZWEJ3YkdVdVkyOXRMMkZ3Y0d4bGQzZGtjbU5oWldOakxtTnliREFPQmdOVkhROEJBZjhFQkFNQ0F5Z3dUd1lKS29aSWh2ZGpaQVlnQkVJTVFEVTRNREpFTVVNM056UkdNRGsyTWtZNE1URXlORGhGTlRNNFJFVXpRa1ZHTmpnd1F6YzVPRFpDUWpWQ05FUkRSVEJDTlRZeU5EbEdNemREUWtJNU5ETXdDZ1lJS29aSXpqMEVBd0lEUndBd1JBSWdUanRpWWprL0JLcDNWOERnNm1JbGNtNUZDT0YwNnp1YjdKc3I2d0NzdktBQ0lIOFUxMTRESTVIbm1mY052d000UlhGRFBUb29wNCtqak1BUHZpZGlwS2tnCi0tLS0tRU5EIENFUlRJRklDQVRFLS0tLS0="; //pragma: allowlist secret
        
        when(mockWorldpayConfig.getApplePayConfig()).thenReturn(mockApplePayConfig);
        when(mockApplePayConfig.getPrimaryPrivateKey()).thenReturn(invalidPrimaryKey);
        when(mockApplePayConfig.getPrimaryPublicCertificate()).thenReturn(invalidPrimaryCert);
        when(mockApplePayConfig.getSecondaryPrivateKey()).thenReturn(Optional.of(ENCODED_PRIMARY_PRIVATE_KEY));
        when(mockApplePayConfig.getSecondaryPublicCertificate()).thenReturn(Optional.of(ENCODED_PRIMARY_PUBLIC_CERTIFICATE));
        var applePayDecrypterWithSecondaryKeyCert = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);

        assertApplePayAuthRequest(applePayDecrypterWithSecondaryKeyCert.performDecryptOperation(applePayAuthRequest));
    }
    
    @Test
    void should_decrypt_data_when_primary_private_key_and_public_certificate_are_valid() {
        assertApplePayAuthRequest(applePayDecrypter.performDecryptOperation(applePayAuthRequest));
    }

    private void assertApplePayAuthRequest(AppleDecryptedPaymentData appleDecryptedPaymentData) {
        assertThat(appleDecryptedPaymentData.getCardExpiryDate().get(), is(LocalDate.of(2020, 7, 31)));
        assertThat(appleDecryptedPaymentData.getApplicationPrimaryAccountNumber(), is("4109370251004320"));
        assertThat(appleDecryptedPaymentData.getCurrencyCode(), is("840"));
        assertThat(appleDecryptedPaymentData.getDeviceManufacturerIdentifier(), is("040010030273"));
        assertThat(appleDecryptedPaymentData.getTransactionAmount(), is(100L));
        assertThat(appleDecryptedPaymentData.getPaymentDataType(), is("3DSecure"));
        assertThat(appleDecryptedPaymentData.getPaymentData().getOnlinePaymentCryptogram(), is("Af9x/QwAA/DjmU65oyc1MAABAAA="));
        assertThat(appleDecryptedPaymentData.getPaymentData().getEciIndicator(), is("5"));
    }

    @Test
    void should_throw_exception_when_primary_certificate_is_invalid() {
        assertThrows(RuntimeException.class, () -> {
            when(mockApplePayConfig.getPrimaryPublicCertificate()).thenReturn("nope");
            applePayDecrypter = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);
            applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        });
    }

    @Test
    void should_throw_exception_when_primary_private_key_is_invalid() {
        assertThrows(RuntimeException.class, () -> {
            when(mockApplePayConfig.getPrimaryPrivateKey()).thenReturn("nope");
            applePayDecrypter = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);
            applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        });
    }

    @Test
    void shouldThrowException_whenEphemeralKeyIsInvalid() {
        assertThrows(InvalidKeyException.class, () -> {
            ApplePayAuthRequest applePayAuthRequest = anApplePayToken().withEphemeralPublicKey("nope").build();
            applePayDecrypter = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);
            applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        });
    }

    @Test
    void shouldThrowException_whenDataIsInvalid() {
        assertThrows(InvalidKeyException.class, () -> {
            ApplePayAuthRequest applePayAuthRequest = anApplePayToken().withData("nope").build();
            applePayDecrypter = new ApplePayDecrypter(mockWorldpayConfig, objectMapper);
            applePayDecrypter.performDecryptOperation(applePayAuthRequest);
        });
    }
}
