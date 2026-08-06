package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.adyen.AdyenApplePayAuthoriseRequestFactory;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenApplePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenCredentialsHelper;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenMerchantAccountHelper;
import uk.gov.pay.connector.gateway.model.request.records.AdyenApplePayAuthorisePayload;
import uk.gov.pay.connector.gateway.util.ChargeFrontendUrlHelper;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdyenApplePayAuthorisePayloadFactoryTest {
    
    @Mock
    private AdyenMerchantAccountHelper mockAdyenMerchantAccountHelper;
    @Mock
    private AdyenCredentialsHelper mockAdyenCredentialsHelper;
    @Mock
    private ChargeFrontendUrlHelper mockChargeFrontendUrlHelper;

    @Test
    void create() throws JsonProcessingException {
        long amountInPence = 2000L;
        String externalId = "abcdefghijklmnopqrstuvwxyz";
        String paymentData = "foo";
        String merchantAccountId = "merchantAccountId";
        String storeId = "storeId";
        String frontendUrl = "http://frontend.test/card_details/" + externalId;
        
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(amountInPence)
                .withExternalId(externalId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestFixture.anApplePayAuthRequest().withApplePaymentData(paymentData).build();

        ApplePayAuthorisationGatewayRequest applePayAuthorisationGatewayRequest = ApplePayAuthorisationGatewayRequest.valueOf(chargeEntity, applePayAuthRequest);
        
        given(mockAdyenMerchantAccountHelper.getMerchantAccount(gatewayAccountEntity)).willReturn(merchantAccountId);
        given(mockAdyenCredentialsHelper.getStore(applePayAuthorisationGatewayRequest)).willReturn(storeId);
        given(mockChargeFrontendUrlHelper.getFrontendUrlForCharge(externalId)).willReturn(frontendUrl);
        
        AdyenApplePayAuthoriseRequestFactory adyenApplePayAuthoriseRequestFactory = new AdyenApplePayAuthoriseRequestFactory(mockAdyenMerchantAccountHelper, mockAdyenCredentialsHelper, mockChargeFrontendUrlHelper);
        
        var actual = adyenApplePayAuthoriseRequestFactory.create(applePayAuthorisationGatewayRequest);

        var expected = new AdyenApplePayAuthorisePayload(
                merchantAccountId,
                storeId,
                externalId, 
                new Amount("GBP", amountInPence),
                new AdyenApplePayPaymentMethod(paymentData),
                frontendUrl
        );
        
        assertThat(actual, is(expected));
    }
    
}
