package uk.gov.pay.connector.gateway.adyen.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.app.adyen.HmacKeys;
import uk.gov.pay.connector.app.adyen.WebhookHmacKeys;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenWebhookType;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil.getHmacKeyForWebhookType;
import static uk.gov.pay.connector.gateway.adyen.webhook.AdyenWebhookType.TOKENS;

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
        
        @ParameterizedTest
        @EnumSource(AdyenWebhookType.class)
        void shouldGetHMACKeyBasedOnType(AdyenWebhookType adyenWebhookType) {
            var expectedValue = "live-hmac-key";
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            var mockHmacKey =  adyenWebhookType == TOKENS ? mockHmacKeys.tokens() : mockHmacKeys.payments();
            when(mockHmacKey).thenReturn(mockKeyPair);
            when(mockKeyPair.live()).thenReturn(mockLiveKeys);
            when(mockLiveKeys.getPrimary()).thenReturn(Optional.of(expectedValue));
            var hmacKey = getHmacKeyForWebhookType(mockAdyenGatewayConfig, adyenWebhookType, true);

            assertThat(hmacKey, is(expectedValue));

            verify(mockKeyPair).live();
        }
        
    }
    
    @Nested
    class TestGetsTokenHmacKeys {

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
            var expectedValue = "live-hmac-key";
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            when(mockHmacKeys.tokens()).thenReturn(mockKeyPair);
            when(mockKeyPair.live()).thenReturn(mockLiveKeys);
            when(mockLiveKeys.getPrimary()).thenReturn(Optional.of(expectedValue));

            String result = AdyenConfigUtil.getTokenHmacKey(mockAdyenGatewayConfig, true);

            assertThat(result, is(expectedValue));

            verify(mockKeyPair).live();
            verify(mockKeyPair, never()).test();
        }

        @Test
        void shouldReturnTestHmacKeyWhenLiveIsFalse() {
            var expectedValue = "test-hmac-key";
            when(mockAdyenGatewayConfig.getHmacKeys()).thenReturn(mockHmacKeys);
            when(mockHmacKeys.tokens()).thenReturn(mockKeyPair);
            when(mockKeyPair.test()).thenReturn(mockTestKeys);
            when(mockTestKeys.getPrimary()).thenReturn(Optional.of(expectedValue));

            String result = AdyenConfigUtil.getTokenHmacKey(mockAdyenGatewayConfig, false);

            assertThat(result, is(expectedValue));

            verify(mockKeyPair).test();
            verify(mockKeyPair, never()).live();
        }
    }
}
