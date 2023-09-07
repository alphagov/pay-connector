package uk.gov.pay.connector.wallets.applepay;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.wallets.WalletAuthoriseService;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@ExtendWith(MockitoExtension.class)
class ApplePayServiceTest {

    @Mock
    private WalletAuthoriseService mockedApplePayAuthoriseService;

    private ApplePayService applePayService;

    @BeforeEach
    void setUp() {
        applePayService = new ApplePayService(mockedApplePayAuthoriseService);
    }

    @Test
    void shouldDecryptAndAuthoriseAValidCharge() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();

        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    void shouldReturnInternalServerError_ifGatewayErrors() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockedApplePayAuthoriseService.doAuthorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = applePayService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockedApplePayAuthoriseService).doAuthorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse) authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }
    
 
}
