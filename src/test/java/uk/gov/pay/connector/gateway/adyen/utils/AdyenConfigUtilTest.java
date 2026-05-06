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
import uk.gov.pay.connector.gateway.adyen.utils.AdyenConfigUtil;

import static org.assertj.core.api.Assertions.assertThat;
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

            assertThat(result).isEqualTo("live-api-key");
        }

        @Test
        void shouldReturnTestApiKeyWhenLiveIsFalse() {
            when(mockCompanyApiKeys.test()).thenReturn("test-api-key");

            String result = AdyenConfigUtil.getCompanyApiKey(mockAdyenGatewayConfig, false);

            assertThat(result).isEqualTo("test-api-key");
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

            assertThat(result).isEqualTo("https://checkout-live.adyen.com");
        }

        @Test
        void shouldReturnTestCheckoutUrlWhenLiveIsFalse() {
            when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);
            when(mockBaseUrls.checkout()).thenReturn(mockCheckoutUrl);
            when(mockCheckoutUrl.test()).thenReturn("https://checkout-test.adyen.com");

            String result = AdyenConfigUtil.getBaseCheckoutUrl(mockAdyenGatewayConfig, false);

            assertThat(result).isEqualTo("https://checkout-test.adyen.com");
        }
    }

    @Nested
    class TestGetMerchantAccountId {
        @Test
        void shouldReturnLiveMerchantAccountIdWhenLiveIsTrue() {
            when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(mockMerchantAccountIds);
            when(mockMerchantAccountIds.live()).thenReturn("live-merchant-123");

            String result = AdyenConfigUtil.getMerchantAccountId(mockAdyenGatewayConfig, true);

            assertThat(result).isEqualTo("live-merchant-123");

            verify(mockMerchantAccountIds).live();
            verify(mockMerchantAccountIds, never()).test();
        }

        @Test
        void shouldReturnTestMerchantAccountIdWhenLiveIsFalse() {
            when(mockAdyenGatewayConfig.getMerchantAccountIds()).thenReturn(mockMerchantAccountIds);
            when(mockMerchantAccountIds.test()).thenReturn("test-merchant-123");

            String result = AdyenConfigUtil.getMerchantAccountId(mockAdyenGatewayConfig, false);

            assertThat(result).isEqualTo("test-merchant-123");

            verify(mockMerchantAccountIds).test();
            verify(mockMerchantAccountIds, never()).live();
        }
    }
}
