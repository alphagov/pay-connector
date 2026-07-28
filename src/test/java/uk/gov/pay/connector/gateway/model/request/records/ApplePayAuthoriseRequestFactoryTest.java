package uk.gov.pay.connector.gateway.model.request.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.gateway.PaymentGatewayName;
import uk.gov.pay.connector.gatewayaccount.model.AdyenCredentials;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;
import uk.gov.pay.connector.model.domain.applepay.ApplePayAuthRequestFixture;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.BDDMockito.given;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntityFixture.aGatewayAccountCredentialsEntity;

@ExtendWith(MockitoExtension.class)
class ApplePayAuthoriseRequestFactoryTest {

    @Mock
    private AdyenApplePayAuthoriseRequestFactory mockAdyenApplePayAuthoriseRequestFactory;
    @Mock
    private AdyenMerchantAccountHelper mockAdyenMerchantAccountHelper;
    @Mock
    private AdyenCredentialsHelper mockAdyenCredentialsHelper;
    @Mock
    private ChargeFrontendUrlHelper mockChargeFrontendUrlHelper;
    
    private ApplePayAuthoriseRequestFactory authoriseRequestFactory;

    private final AdyenCredentials adyenCredentials = new AdyenCredentials(
            "legalEntityId",
            "storeId",
            "accountHolderId",
            "balanceAccountId"
    );

    @BeforeEach
    void setUp() {
        authoriseRequestFactory = new ApplePayAuthoriseRequestFactory(mockAdyenApplePayAuthoriseRequestFactory);

    }

    @Test
    void shouldBuildWorldpayAuthoriseRequestIfWorldpay() {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(PaymentGatewayName.ADYEN.getName())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestFixture.anApplePayAuthRequest().build();

        ApplePayAuthorisationGatewayRequest applePayAuthorisationGatewayRequest = ApplePayAuthorisationGatewayRequest.valueOf(chargeEntity, applePayAuthRequest);
        
        AdyenApplePayAuthoriseRequest adyenApplePayAuthoriseRequest = AdyenApplePayAuthoriseRequestFixture.anAdyenApplePayAuthoriseRequestFixture().build();

        given(mockAdyenApplePayAuthoriseRequestFactory.create(applePayAuthorisationGatewayRequest)).willReturn(adyenApplePayAuthoriseRequest);

        Optional<? extends ApplePayAuthoriseRequest> applePayAuthoriseRequest = authoriseRequestFactory.create(applePayAuthorisationGatewayRequest);
        
        assertThat(applePayAuthoriseRequest.isPresent(), is(true));
        assertThat(applePayAuthoriseRequest.get(), is(adyenApplePayAuthoriseRequest));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentGatewayName.class,  mode = EXCLUDE, names = { "ADYEN" })
    void shouldBuildNothingIfNotWorldpay(PaymentGatewayName paymentGatewayName) {
        GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity = aGatewayAccountCredentialsEntity()
                .withPaymentProvider(paymentGatewayName.getName())
                .build();

        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayAccountCredentials(List.of(gatewayAccountCredentialsEntity))
                .build();

        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withGatewayAccountEntity(gatewayAccountEntity)
                .build();
        
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestFixture.anApplePayAuthRequest().build();

        ApplePayAuthorisationGatewayRequest applePayAuthorisationGatewayRequest = ApplePayAuthorisationGatewayRequest.valueOf(chargeEntity, applePayAuthRequest);

        Optional<? extends ApplePayAuthoriseRequest> applePayAuthoriseRequest = authoriseRequestFactory.create(applePayAuthorisationGatewayRequest);
        
        assertThat(applePayAuthoriseRequest.isEmpty(), is(true));
    }

}
