package uk.gov.pay.connector.gateway.worldpay.applepay;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static fj.data.Either.left;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.GatewayError.unexpectedStatusCodeFromGateway;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayApplePayAuthorisationHandlerTest {

    
    @Mock
    private GatewayClient mockGatewayClient;
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    private WorldpayApplePayAuthorisationHandler worldpayApplePayAuthorisationHandler;
    
    
    @Before
    public void setUp() {
        worldpayApplePayAuthorisationHandler = new WorldpayApplePayAuthorisationHandler(mockGatewayClient);
    }
    
    @Test
    public void shouldSendApplePayRequestWhenApplePayDetailsArePresent() throws IOException, SAXException {
        ChargeEntity chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription("This is the description").build();
        chargeEntity.setGatewayTransactionId("MyUniqueTransactionId!");
        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenReturn(left(unexpectedStatusCodeFromGateway("Unexpected HTTP status code 400 from gateway")));

        worldpayApplePayAuthorisationHandler.authorise(getApplePayAuthorisationRequest(chargeEntity));

        ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);

        verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());

        assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
    }

    private ApplePayAuthorisationGatewayRequest getApplePayAuthorisationRequest(ChargeEntity chargeEntity) {
        AppleDecryptedPaymentData data = new AppleDecryptedPaymentData(
                new WalletPaymentInfo(
                        "4242",
                        "visa",
                        PayersCardType.DEBIT,
                        "Mr. Payment",
                        "aaa@bbb.test"
                ),
                "4818528840010767",
                LocalDate.of(2023, 12, 31),
                "643",
                10L,
                "040010030273",
                "3DSecure",
                new AppleDecryptedPaymentData.PaymentData(
                        "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                        "7"
                )
        );
        return new ApplePayAuthorisationGatewayRequest(chargeEntity, data);
    }

}
