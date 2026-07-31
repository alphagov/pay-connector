package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.adyen.AdyenGatewayConfig;
import uk.gov.pay.connector.app.adyen.AdyenIds;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AdyenMerchantAccountHelperTest {

    private static final String LIVE_MERCHANT_ID = "live-merchant-123";
    private static final String TEST_MERCHANT_ID = "test-merchant-123";
    
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
        lenient().when(mockAdyenIds.live()).thenReturn(LIVE_MERCHANT_ID);
        lenient().when(mockAdyenIds.test()).thenReturn(TEST_MERCHANT_ID);

        adyenMerchantAccountHelper = new AdyenMerchantAccountHelper(mockConnectorConfiguration);
    }

    @ParameterizedTest(name = "{0} account type returns merchant id {1}")
    @MethodSource("gatewayAccountTypes")
    void shouldReturnMerchantAccountForGatewayAccountEntity(GatewayAccountType accountType, String expectedMerchantId) {
        var gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().withType(accountType).build();

        assertThat(adyenMerchantAccountHelper.getMerchantAccount(gatewayAccountEntity), is(expectedMerchantId));
    }

    @ParameterizedTest(name = "live={0} returns merchant id {1}")
    @MethodSource("liveFlags")
    void shouldReturnMerchantAccountForLiveFlag(boolean live, String expectedMerchantId) {
        assertThat(adyenMerchantAccountHelper.getMerchantAccount(live), is(expectedMerchantId));
    }

    private static Stream<Arguments> gatewayAccountTypes() {
        return Stream.of(
                arguments(GatewayAccountType.LIVE, LIVE_MERCHANT_ID),
                arguments(GatewayAccountType.TEST, TEST_MERCHANT_ID)
        );
    }

    private static Stream<Arguments> liveFlags() {
        return Stream.of(
                arguments(true, LIVE_MERCHANT_ID),
                arguments(false, TEST_MERCHANT_ID)
        );
    }

}
