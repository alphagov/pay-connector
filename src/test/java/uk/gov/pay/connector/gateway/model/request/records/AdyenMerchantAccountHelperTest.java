package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdyenMerchantAccountHelperTest {
    
    private AdyenMerchantAccountHelper adyenMerchantAccountHelper;
    
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private AdyenGatewayConfig mockAdyenGatewayConfig;
    @Mock
    private AdyenIds mockAdyenIds;
    
    @BeforeEach
    void setUp() {
        given(mockConnectorConfiguration.getAdyenGatewayConfig()).willReturn(mockAdyenGatewayConfig);
        given(mockAdyenGatewayConfig.getMerchantAccountIds()).willReturn(mockAdyenIds);
        
        adyenMerchantAccountHelper = new AdyenMerchantAccountHelper(mockConnectorConfiguration);
    }

    @Test
    void shouldReturnLiveMerchantAccountIdWhenLiveIsTrue() {
        when(mockAdyenIds.live()).thenReturn("live-merchant-123");

        var gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().withType(GatewayAccountType.LIVE).build();
        String result = adyenMerchantAccountHelper.getMerchantAccount(gatewayAccountEntity);

        assertThat(result, is("live-merchant-123"));

        verify(mockAdyenIds).live();
        verify(mockAdyenIds, never()).test();
    }

    @Test
    void shouldReturnTestMerchantAccountIdWhenLiveIsFalse() {
        when(mockAdyenIds.test()).thenReturn("test-merchant-123");

        var gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().withType(GatewayAccountType.TEST).build();
        String result = adyenMerchantAccountHelper.getMerchantAccount(gatewayAccountEntity);

        assertThat(result, is("test-merchant-123"));

        verify(mockAdyenIds).test();
        verify(mockAdyenIds, never()).live();
    }

}
