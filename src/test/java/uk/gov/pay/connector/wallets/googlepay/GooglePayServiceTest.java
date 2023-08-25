package uk.gov.pay.connector.wallets.googlepay;

import com.amazonaws.util.json.Jackson;
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
import uk.gov.pay.connector.wallets.googlepay.api.GooglePayAuthRequest;

import javax.ws.rs.core.Response;
import java.io.IOException;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.ErrorType.GATEWAY_ERROR;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@ExtendWith(MockitoExtension.class)
class GooglePayServiceTest {

    @Mock
    private WalletAuthoriseService mockedWalletAuthoriseService;
    @Mock
    private WorldpayOrderStatusResponse worldpayResponse;

    private GooglePayService googlePayService;
    private GooglePayAuthRequest googlePayAuthRequest;
    private GooglePayAuthRequest googlePayAuth3dsRequest;
    
    @BeforeEach
    void setUp() throws IOException {
        googlePayService = new GooglePayService(mockedWalletAuthoriseService);
        googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
        googlePayAuth3dsRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-3ds-auth-request.json"), GooglePayAuthRequest.class);
    }
    
    @Test
    void shouldAuthoriseAValidCharge() {
        String externalChargeId = "external-charge-id";
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockedWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = googlePayService.authorise(externalChargeId, googlePayAuthRequest, "worldpay");

        verify(mockedWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    void shouldReturnAuthorise3dsRequiredForAValid3dsCharge() {
        String externalChargeId = "external-charge-id";
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS);
        when(mockedWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuth3dsRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = googlePayService.authorise(externalChargeId, googlePayAuth3dsRequest, "worldpay");

        verify(mockedWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuth3dsRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION 3DS REQUIRED")));
    }

    @Test
    void shouldReturnInternalServerError_ifGatewayErrors() {
        String externalChargeId = "external-charge-id";
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockedWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = googlePayService.authorise(externalChargeId, googlePayAuthRequest, "worldpay");

        verify(mockedWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse)authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }
}
