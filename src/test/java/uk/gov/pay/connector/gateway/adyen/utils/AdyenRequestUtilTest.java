package uk.gov.pay.connector.gateway.adyen.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.app.adyen.ApiKeys;
import uk.gov.pay.connector.app.adyen.BaseUrls;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture.aCardAuthorisationGatewayRequest;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.model.domain.AuthCardDetailsFixture.anAuthCardDetails;

@ExtendWith(MockitoExtension.class)
class AdyenRequestUtilTest {

    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;
    public final AdyenCredentials adyenCredentials = new AdyenCredentials("legal_entity_id", "store_id");
    private CardAuthorisationGatewayRequest mockAuthoriseRequest;

    @BeforeEach
    void setUp() {
        mockAuthoriseRequest = aCardAuthorisationGatewayRequest()
                .withAuthCardDetails(anAuthCardDetails().build())
                .withCredentials(adyenCredentials)
                .withGatewayAccount(aGatewayAccountEntity().withType(TEST).build())
                .build();
    }

    @Test
    void should_create_adyen_checkout_authorisation_url() {
        BaseUrls mockBaseUrls = mock(BaseUrls.class);
        when(mockBaseUrls.checkout()).thenReturn(new BaseUrls.CheckoutUrls("https://example.com/test/v71", "https://example.com/live/v71"));
        when(mockAdyenGatewayConfig.getBaseUrls()).thenReturn(mockBaseUrls);

        var testCheckoutUrl = AdyenRequestUtil.getAuthUrl(mockAdyenGatewayConfig, mockAuthoriseRequest).toString();
        assertThat(testCheckoutUrl, is("https://example.com/test/v71/payments"));
    }

    @Test
    void should_create_api_key_headers_for_checkout_url() {
        ApiKeys mockApiKeys = mock(ApiKeys.class);
        ApiKeys.CompanyAccountApiKeys mockCompanyApiKeys = mock(ApiKeys.CompanyAccountApiKeys.class);
        when(mockApiKeys.companyAccount()).thenReturn(mockCompanyApiKeys);
        when(mockCompanyApiKeys.test()).thenReturn("test");
        when(mockAdyenGatewayConfig.getApiKeys()).thenReturn(mockApiKeys);

        var headers = AdyenRequestUtil.getHeaders(mockAdyenGatewayConfig, mockAuthoriseRequest);
        assertThat(headers, hasEntry("X-API-Key", "test"));
    }
}
