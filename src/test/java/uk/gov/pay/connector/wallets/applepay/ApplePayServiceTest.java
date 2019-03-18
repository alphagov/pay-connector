package uk.gov.pay.connector.wallets.applepay;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.CLIENT_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayDecryptedPaymentDataFixture.anApplePayDecryptedPaymentData;
import static uk.gov.pay.connector.model.domain.applepay.ApplePayPaymentInfoFixture.anApplePayPaymentInfo;

@RunWith(MockitoJUnitRunner.class)
public class ApplePayServiceTest {

    @Mock
    private ApplePayDecrypter mockedApplePayDecrypter;

    @Mock
    private WalletAuthoriseService mockedApplePayAuthoriseService;

    private ApplePayService applePayService;
    private AppleDecryptedPaymentData validData =
            anApplePayDecryptedPaymentData()
                    .withApplePaymentInfo(
                            anApplePayPaymentInfo().build())
                    .build();
    @Before
    public void setUp() {
        applePayService = new ApplePayService(mockedApplePayDecrypter, mockedApplePayAuthoriseService);
    }
    
    @Test
    public void shouldDecryptAndAuthoriseAValidCharge() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
                
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier("234")
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockedApplePayDecrypter.performDecryptOperation(applePayAuthRequest)).thenReturn(validData);
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, validData)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, validData);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    public void shouldReturnInternalServerError_ifGatewayErrors() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier("234")
                .build();
        when(gatewayError.getErrorType()).thenReturn(CLIENT_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockedApplePayDecrypter.performDecryptOperation(applePayAuthRequest)).thenReturn(validData);
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, validData)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, validData);
        assertThat(authorisationResponse.getStatus(), is(500));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("message", "oops")));
    }
}
