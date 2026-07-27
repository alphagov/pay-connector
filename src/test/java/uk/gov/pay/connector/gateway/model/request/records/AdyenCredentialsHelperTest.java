package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequest;
import uk.gov.pay.connector.gateway.model.request.CardAuthorisationGatewayRequestFixture;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayCredentials;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AdyenCredentialsHelperTest {
    private final AdyenCredentialsHelper adyenCredentialsHelper = new AdyenCredentialsHelper();
    
    @Mock
    private GatewayCredentials mockGatewayCredentials;
    
    private final AdyenCredentials adyenCredentials = new AdyenCredentials(
            "legalEntityId", 
            "storeId",
            "accountHolderId",
            "balanceAccountId"
    );

    @Test
    void getAdyenCredentials() {
        CardAuthorisationGatewayRequest gatewayRequest = CardAuthorisationGatewayRequestFixture
                .aCardAuthorisationGatewayRequest()
                .withCredentials(adyenCredentials)
                .build();
        
        var result = adyenCredentialsHelper.getStore(gatewayRequest);
        
        assertThat(result, is("storeId"));
    }

    @Test
    void getAdyenCredentialsThrowsExceptionIfNotAdyenCredentials() {
        CardAuthorisationGatewayRequest gatewayRequest = CardAuthorisationGatewayRequestFixture
                .aCardAuthorisationGatewayRequest()
                .withCredentials(mockGatewayCredentials)
                .build();
        
        assertThrows(IllegalArgumentException.class, () -> adyenCredentialsHelper.getStore(gatewayRequest));
        
    }

}
