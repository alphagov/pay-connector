package uk.gov.pay.connector.wallets.googlepay;

import com.amazonaws.util.json.Jackson;
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
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_URL_DNS_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@RunWith(MockitoJUnitRunner.class)
public class GooglePayServiceTest {

    @Mock
    private WalletAuthoriseService mockedWalletAuthoriseService;

    private GooglePayService googlePayService;
    private GooglePayAuthRequest googlePayAuthRequest;
    
    @Before
    public void setUp() throws IOException {
        googlePayService = new GooglePayService(mockedWalletAuthoriseService);
        googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
    }
    
    @Test
    public void shouldAuthoriseAValidCharge() throws IOException {
        String externalChargeId = "external-charge-id";

        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier("234")
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockedWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = googlePayService.authorise(externalChargeId, googlePayAuthRequest);

        verify(mockedWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    public void shouldReturnInternalServerError_ifGatewayErrors() throws IOException {
        String externalChargeId = "external-charge-id";
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier("234")
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_URL_DNS_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockedWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = googlePayService.authorise(externalChargeId, googlePayAuthRequest);

        verify(mockedWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(500));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("message", "oops")));
    }
}
