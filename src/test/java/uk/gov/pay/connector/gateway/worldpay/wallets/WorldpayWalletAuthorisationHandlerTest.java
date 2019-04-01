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
import uk.gov.pay.connector.gateway.GatewayException.GatewayErrorException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.util.AuthUtil;
import uk.gov.pay.connector.gateway.worldpay.applepay.WorldpayWalletAuthorisationHandler;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.model.domain.ChargeEntityFixture;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;
import uk.gov.pay.connector.wallets.WalletAuthorisationGatewayRequest;
import uk.gov.pay.connector.wallets.applepay.AppleDecryptedPaymentData;
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;
import uk.gov.pay.connector.wallets.model.WalletPaymentInfo;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity.Type.TEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST;

@RunWith(MockitoJUnitRunner.class)
public class WorldpayWalletAuthorisationHandlerTest {

    @Mock
    private GatewayClient mockGatewayClient;
    private GatewayAccountEntity gatewayAccountEntity = new GatewayAccountEntity("worldpay", new HashMap<>(), TEST);
    private WorldpayWalletAuthorisationHandler worldpayApplePayAuthorisationHandler;
    private ChargeEntity chargeEntity;
    
    private static final URI WORLDPAY_URL = URI.create("http://worldpay.test");

    @Before
    public void setUp() throws Exception {
        worldpayApplePayAuthorisationHandler = new WorldpayWalletAuthorisationHandler(mockGatewayClient, ImmutableMap.of(TEST.toString(), WORLDPAY_URL));
        chargeEntity = ChargeEntityFixture.aValidChargeEntity().withDescription("This is the description").build();
        chargeEntity.setGatewayTransactionId("MyUniqueTransactionId!");
        chargeEntity.setGatewayAccount(gatewayAccountEntity);
        gatewayAccountEntity.setCredentials(ImmutableMap.of("merchant_id", "MERCHANTCODE"));
        when(mockGatewayClient.postRequestFor(any(URI.class), any(GatewayAccountEntity.class), any(GatewayOrder.class), anyMap()))
                .thenThrow(new GatewayErrorException("Unexpected HTTP status code 400 from gateway"));
    }

    @Test
    public void shouldSendApplePayRequestWhenApplePayDetailsArePresent() throws Exception {
        try {
            worldpayApplePayAuthorisationHandler.authorise(getApplePayAuthorisationRequest());
        } catch (GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_APPLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            assertThat(headers.getValue(), is(AuthUtil.getGatewayAccountCredentialsAsAuthHeader(gatewayAccountEntity)));
        }
    }

    @Test
    public void shouldSendGooglePayRequestWhenGooglePayDetailsArePresent() throws Exception {
        try {
            worldpayApplePayAuthorisationHandler.authorise(getGooglePayAuthorisationRequest());
        } catch (GatewayErrorException e) {
            ArgumentCaptor<GatewayOrder> gatewayOrderArgumentCaptor = ArgumentCaptor.forClass(GatewayOrder.class);
            ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
            verify(mockGatewayClient).postRequestFor(eq(WORLDPAY_URL), eq(gatewayAccountEntity), gatewayOrderArgumentCaptor.capture(), headers.capture());
            assertXMLEqual(TestTemplateResourceLoader.load(WORLDPAY_VALID_AUTHORISE_WORLDPAY_GOOGLE_PAY_REQUEST), gatewayOrderArgumentCaptor.getValue().getPayload());
            assertThat(headers.getValue().size(), is(1));
            assertThat(headers.getValue(), is(AuthUtil.getGatewayAccountCredentialsAsAuthHeader(gatewayAccountEntity)));
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
