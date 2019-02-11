package uk.gov.pay.connector.gateway.worldpay.wallets;

import com.amazonaws.util.json.Jackson;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayErrorException.GatewayConnectionErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayWalletAuthorisationHandlerTest {

    @Mock
    private GatewayClient mockGatewayClient;
    @Mock
    private GatewayAccountEntity mockGatewayAccountEntity;
    private WorldpayWalletAuthorisationHandler worldpayApplePayAuthorisationHandler;
    private ChargeEntity chargeEntity;


    @Before
    public void setUp() throws Exception {
        worldpayApplePayAuthorisationHandler = new WorldpayWalletAuthorisationHandler(mockGatewayClient);
        chargeEntity = ChargeEntityFixture.aValidChargeEntity()
                .withDescription("This is the description").build();
        chargeEntity.setGatewayTransactionId("MyUniqueTransactionId!");
        chargeEntity.setGatewayAccount(mockGatewayAccountEntity);

        Map<String, String> credentialsMap = ImmutableMap.of("merchant_id", "MERCHANTCODE");
        when(mockGatewayAccountEntity.getCredentials()).thenReturn(credentialsMap);
        when(mockGatewayClient.postRequestFor(isNull(), any(GatewayAccountEntity.class), any(GatewayOrder.class)))
                .thenThrow(new GatewayConnectionErrorException("Unexpected HTTP status code 400 from gateway"));
    }

    @Test
    public void shouldSendApplePayRequestWhenApplePayDetailsArePresent() throws Exception {
        try {
            worldpayApplePayAuthorisationHandler.authorise(getApplePayAuthorisationRequest());
        } catch (GatewayConnectionErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }

    @Test
    public void shouldSendGooglePayRequestWhenGooglePayDetailsArePresent() throws Exception {
        try {
            worldpayApplePayAuthorisationHandler.authorise(getGooglePayAuthorisationRequest());
        } catch (GatewayConnectionErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            verify(mockGatewayClient).postRequestFor(eq(null), eq(mockGatewayAccountEntity), gatewayOrderArgumentCaptor.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
        }
    }

    private WalletAuthorisationGatewayRequest getGooglePayAuthorisationRequest() throws IOException {
        GooglePayAuthRequest googlePayAuthRequest =
                Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
        return new WalletAuthorisationGatewayRequest(chargeEntity, googlePayAuthRequest);
    }

    private WalletAuthorisationGatewayRequest getApplePayAuthorisationRequest() {
        AppleDecryptedPaymentData data = new AppleDecryptedPaymentData(
                new WalletPaymentInfo(
                        "4242",
                        "visa",
                        PayersCardType.DEBIT,
                        "Mr. Payment",
                        "aaa@bbb.test"
                ),
                "4818528840010767",
                LocalDate.of(2023, 12, 1),
                "643",
                10L,
                "040010030273",
                "3DSecure",
                new AppleDecryptedPaymentData.PaymentData(
                        "Ao/fzpIAFvp1eB9y8WVDMAACAAA=",
                        "7"
                )
        );
        return new WalletAuthorisationGatewayRequest(chargeEntity, data);
    }

}
