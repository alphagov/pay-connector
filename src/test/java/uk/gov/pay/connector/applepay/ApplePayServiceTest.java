package uk.gov.pay.connector.applepay;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.applepay.api.ApplePayToken;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.util.AuthUtils;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_URL_DNS_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@RunWith(MockitoJUnitRunner.class)
public class ApplePayServiceTest {

    @Mock
    private ApplePayDecrypter mockedApplePayDecrypter;

    @Mock
    private AppleAuthoriseService mockedApplePayAuthoriseService;

    private ApplePayService applePayService;
    
    @Before
    public void setUp() {
        applePayService = new ApplePayService(mockedApplePayDecrypter, mockedApplePayAuthoriseService);
    }
    
    @Test
    public void shouldDecryptAndAuthoriseAValidCharge() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayToken applePayToken = ApplePayTokenBuilder.anApplePayToken().build();
        AppleDecryptedPaymentData decryptedPaymentData = AuthUtils.ApplePay.buildDecryptedPaymentData("Mr. Payment", "mr@payment.test", "4242424242424242");
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier("234")
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockedApplePayDecrypter.performDecryptOperation(applePayToken)).thenReturn(decryptedPaymentData);
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, decryptedPaymentData)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayToken);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, decryptedPaymentData);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    public void shouldReturnInternalServerError_ifGatewayErrors() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayToken applePayToken = ApplePayTokenBuilder.anApplePayToken().build();
        AppleDecryptedPaymentData decryptedPaymentData = AuthUtils.ApplePay.buildDecryptedPaymentData("Mr. Payment", "mr@payment.test", "4242424242424242");
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier("234")
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_URL_DNS_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockedApplePayDecrypter.performDecryptOperation(applePayToken)).thenReturn(decryptedPaymentData);
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, decryptedPaymentData)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayToken);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, decryptedPaymentData);
        assertThat(authorisationResponse.getStatus(), is(500));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("message", "oops")));
    }
}
