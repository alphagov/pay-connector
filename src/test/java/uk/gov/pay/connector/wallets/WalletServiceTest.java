package uk.gov.pay.connector.wallets;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthRequestBuilder;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
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
public class WalletServiceTest {

    @Mock
    private WalletAuthoriseService mockWalletAuthoriseService;

    @Mock
    private WorldpayOrderStatusResponse worldpayResponse;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(mockWalletAuthoriseService);
    }

    @Test
    void shouldAuthoriseAValidChargeForApplePay() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockWalletAuthoriseService.doAuthorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockWalletAuthoriseService).doAuthorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    void shouldReturnInternalServerError_ifGatewayErrorsForApplePay() throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockWalletAuthoriseService.doAuthorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockWalletAuthoriseService).doAuthorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse) authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }

    @Test
    void shouldAuthoriseAValidChargeForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        GooglePayAuthRequest googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, googlePayAuthRequest);

        verify(mockWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    void shouldReturnAuthorise3dsRequiredForAValid3dsChargeForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        GooglePayAuthRequest googlePayAuth3dsRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-3ds-auth-request.json"), GooglePayAuthRequest.class);
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS);
        when(mockWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuth3dsRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, googlePayAuth3dsRequest);

        verify(mockWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuth3dsRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(ImmutableMap.of("status", "AUTHORISATION 3DS REQUIRED")));
    }

    @Test
    void shouldReturnInternalServerError_ifGatewayErrorsForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        GooglePayAuthRequest googlePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), GooglePayAuthRequest.class);
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockWalletAuthoriseService.doAuthorise(externalChargeId, googlePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, googlePayAuthRequest);

        verify(mockWalletAuthoriseService).doAuthorise(externalChargeId, googlePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse)authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }


}
