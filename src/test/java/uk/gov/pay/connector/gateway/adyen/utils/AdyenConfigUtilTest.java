package uk.gov.pay.connector.gateway.adyen.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenConfigUtilTest {

    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;
    @Mock
    private ApiKeys mockApiKeys;
    @Mock
    private ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys;
    @Mock
    private BaseUrls mockBaseUrls;
    @Mock
    private BaseUrls.CheckoutUrls mockCheckoutUrl;
    @Mock
    private AdyenIds mockMerchantAccountIds;

    @Nested
    class TestGetCompanyApiKey {

        @BeforeEach
        void setUp() {
            when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);
            when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        }

        @Test
        void shouldReturnLiveApiKeyWhenLiveIsTrue() {
            when(mockCompanyApiKeys.live()).thenReturn("live-api-key");

            String result =
                    AdyenConfigUtil.getCompanyApiKey(mockAdyenGatewayConfig, true);

            assertThat(result, is("live-api-key"));
        }

        @Test
        void shouldReturnTestApiKeyWhenLiveIsFalse() {
            when(mockCompanyApiKeys.test()).thenReturn("test-api-key");

            String result = AdyenConfigUtil.getCompanyApiKey(mockAdyenGatewayConfig, false);

            assertThat(result, is("test-api-key"));
        }
    }

    @Nested
    class TestGetBaseCheckoutUrl {
        @Test
        void shouldReturnLiveCheckoutUrlWhenLiveIsTrue() {
            when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);
            when(mockBaseUrls.checkout()).thenReturn(mockCheckoutUrl);
            when(mockCheckoutUrl.live()).thenReturn("https://checkout-live.adyen.com");

            String result = AdyenConfigUtil.getBaseCheckoutUrl(mockAdyenGatewayConfig, true);

            assertThat(result, is("https://checkout-live.adyen.com"));
        }

        @Test
        void shouldReturnTestCheckoutUrlWhenLiveIsFalse() {
            when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);
            when(mockBaseUrls.checkout()).thenReturn(mockCheckoutUrl);
            when(mockCheckoutUrl.test()).thenReturn("https://checkout-test.adyen.com");

            String result = AdyenConfigUtil.getBaseCheckoutUrl(mockAdyenGatewayConfig, false);

            assertThat(result, is("https://checkout-test.adyen.com"));
        }
    }

    @Nested
    class TestGetMerchantAccountId {
        @Test
        void shouldReturnLiveMerchantAccountIdWhenLiveIsTrue() {
            when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(mockMerchantAccountIds);
            when(mockMerchantAccountIds.live()).thenReturn("live-merchant-123");

            String result = AdyenConfigUtil.getMerchantAccountId(mockAdyenGatewayConfig, true);

            assertThat(result, is("live-merchant-123"));

            verify(mockMerchantAccountIds).live();
            verify(mockMerchantAccountIds, never()).test();
        }

        @Test
        void shouldReturnTestMerchantAccountIdWhenLiveIsFalse() {
            when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(mockMerchantAccountIds);
            when(mockMerchantAccountIds.test()).thenReturn("test-merchant-123");

            String result = AdyenConfigUtil.getMerchantAccountId(mockAdyenGatewayConfig, false);

            assertThat(result, is("test-merchant-123"));

            verify(mockMerchantAccountIds).test();
            verify(mockMerchantAccountIds, never()).live();
        }
    }

    @Nested
    class TestGetsHmacKeys {

        @Mock
        private HmacKeys mockHmacKeys;
        @Mock
        private HmacKeys.WebhookHmacKeyPair mockKeyPair;
        @Mock
        private WebhookHmacKeys mockLiveKeys;
        @Mock
        private WebhookHmacKeys mockTestKeys;

        @Test
        void shouldReturnLiveHmacKeyWhenLiveIsTrue() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            when(mockHmacKeys.payments()).thenReturn(mockKeyPair);
            when(mockKeyPair.live()).thenReturn(mockLiveKeys);
            when(mockLiveKeys.getPrimary()).thenReturn(Optional.of("live-hmac-key"));

            String result = AdyenConfigUtil.getHmacKey(mockAdyenGatewayConfig, true);

            assertThat(result, is("live-hmac-key"));

            verify(mockKeyPair).live();
            verify(mockKeyPair, never()).test();
        }

        @Test
        void shouldReturnTestHmacKeyWhenLiveIsFalse() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            when(mockHmacKeys.payments()).thenReturn(mockKeyPair);
            when(mockKeyPair.test()).thenReturn(mockTestKeys);
            when(mockTestKeys.getPrimary()).thenReturn(Optional.of("test-hmac-key"));

            String result = AdyenConfigUtil.getHmacKey(mockAdyenGatewayConfig, false);

            assertThat(result, is("test-hmac-key"));

            verify(mockKeyPair).test();
            verify(mockKeyPair, never()).live();
        }

        @Test
        void shouldThrowWhenPrimaryHmacKeyIsMissingForTest() {
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            when(mockHmacKeys.payments()).thenReturn(mockKeyPair);
            when(mockKeyPair.test()).thenReturn(mockTestKeys);
            when(mockTestKeys.getPrimary()).thenReturn(Optional.empty());

            var exception = assertThrows(IllegalStateException.class, () ->
                    AdyenConfigUtil.getHmacKey(mockAdyenGatewayConfig, false));

            assertThat(exception.getMessage(), is("Missing primary Adyen HMAC key"));
        }
    }
}
