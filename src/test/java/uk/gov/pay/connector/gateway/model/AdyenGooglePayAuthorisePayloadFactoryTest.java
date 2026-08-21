package uk.gov.pay.connector.gateway.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.adyen.AdyenBrowserInfoFactory;
import uk.gov.pay.connector.gateway.adyen.AdyenGooglePayAuthorisePayloadFactory;
import uk.gov.pay.connector.gateway.adyen.request.json.AdyenGooglePayPaymentMethod;
import uk.gov.pay.connector.gateway.adyen.request.json.Amount;
import uk.gov.pay.connector.gateway.adyen.response.json.AdyenBrowserInfo;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenCredentialsHelper;
import uk.gov.pay.connector.gateway.adyen.utils.AdyenMerchantAccountHelper;
import uk.gov.pay.connector.gateway.model.request.records.AdyenGooglePayAuthorisePayload;
import uk.gov.pay.connector.gateway.util.ChargeFrontendUrlHelper;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture;
import uk.gov.pay.connector.model.domain.googlepay.AdyenGooglePayAuthRequestFixture;
import uk.gov.pay.connector.wallets.googlepay.GooglePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.googlepay.api.AdyenGooglePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayPaymentInfo;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.model.domain.googlepay.GooglePayPaymentInfoFixture.aGooglePayPaymentInfo;

@ExtendWith(MockitoExtension.class)
class AdyenGooglePayAuthorisePayloadFactoryTest {
    
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
        String merchantAccountId = "merchantAccountId";
        String storeId = "storeId";
        String frontendUrl = "http://frontend.test/card_details/" + externalId;
        GooglePayPaymentInfo googlePaymentInfo = aGooglePayPaymentInfo().build();
        
        GatewayAccountEntity gatewayAccountEntity = GatewayAccountEntityFixture.aGatewayAccountEntity().build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withAmount(amountInPence)
                .withExternalId(externalId)
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();

        AdyenGooglePayAuthRequest googlePayAuthRequest = AdyenGooglePayAuthRequestFixture.aGooglePayAuthRequest().withGooglePaymentInfo(googlePaymentInfo).build();

        GooglePayAuthorisationGatewayRequest googlePayAuthorisationGatewayRequest = GooglePayAuthorisationGatewayRequest.valueOf(chargeEntity, googlePayAuthRequest);

        AdyenBrowserInfo adyenBrowserInfo = new AdyenBrowserInfoFactory().create(googlePayAuthRequest.getPaymentInfo());

        given(mockAdyenMerchantAccountHelper.getMerchantAccount(gatewayAccountEntity)).willReturn(merchantAccountId);
        given(mockAdyenCredentialsHelper.getStore(googlePayAuthorisationGatewayRequest)).willReturn(storeId);
        given(mockChargeFrontendUrlHelper.getFrontendUrlForCharge(externalId)).willReturn(frontendUrl);

        AdyenGooglePayAuthorisePayloadFactory adyenGooglePayAuthoriseRequestFactory = new AdyenGooglePayAuthorisePayloadFactory(mockAdyenMerchantAccountHelper, mockAdyenCredentialsHelper, mockChargeFrontendUrlHelper);
        
        var actual = adyenGooglePayAuthoriseRequestFactory.create(googlePayAuthorisationGatewayRequest);

        var expected = new AdyenGooglePayAuthorisePayload(
                merchantAccountId,
                storeId,
                externalId, 
                new Amount("GBP", amountInPence),
                new AdyenGooglePayPaymentMethod(googlePaymentInfo.toString()),
                adyenBrowserInfo,
                frontendUrl
        );
        
        assertThat(actual, is(expected));
    }
    
}
