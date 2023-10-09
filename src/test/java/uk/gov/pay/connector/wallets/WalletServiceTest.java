package uk.gov.pay.connector.wallets;

import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.common.model.api.ErrorResponse;
import uk.gov.pay.connector.gateway.model.ErrorType;
import uk.gov.pay.connector.gateway.model.GatewayError;
import uk.gov.pay.connector.gateway.model.ProviderSessionIdentifier;
import uk.gov.pay.connector.gateway.model.response.BaseAuthoriseResponse;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;
import uk.gov.pay.connector.wallets.applepay.ApplePayAuthRequestBuilder;
import uk.gov.pay.connector.wallets.applepay.api.ApplePayAuthRequest;
import uk.gov.pay.connector.wallets.googlepay.api.WorldpayGooglePayAuthRequest;
import uk.gov.service.payments.commons.model.ErrorIdentifier;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;

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
        when(mockWalletAuthoriseService.authorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(Map.of("status", "AUTHORISATION SUCCESS")));
    }
    
    @ParameterizedTest
    @EnumSource(ErrorType.class)
    void shouldReturnInternalServerError_ifGatewayErrorsForApplePay(ErrorType errorType) throws IOException {
        String externalChargeId = "external-charge-id";
        ApplePayAuthRequest applePayAuthRequest = ApplePayAuthRequestBuilder.anApplePayToken().build();
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(errorType);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockWalletAuthoriseService.authorise(externalChargeId, applePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, applePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, applePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse) authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }

    @Test
    void shouldAuthoriseAValidChargeForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        WorldpayGooglePayAuthRequest worldpayGooglePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), WorldpayGooglePayAuthRequest.class);
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.AUTHORISED);
        when(mockWalletAuthoriseService.authorise(externalChargeId, worldpayGooglePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, worldpayGooglePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, worldpayGooglePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(Map.of("status", "AUTHORISATION SUCCESS")));
    }

    @Test
    void shouldReturnAuthorise3dsRequiredForAValid3dsChargeForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        WorldpayGooglePayAuthRequest googlePayAuth3dsRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-3ds-auth-request.json"), WorldpayGooglePayAuthRequest.class);
        GatewayResponse<BaseAuthoriseResponse> gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.REQUIRES_3DS);
        when(mockWalletAuthoriseService.authorise(externalChargeId, googlePayAuth3dsRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, googlePayAuth3dsRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, googlePayAuth3dsRequest);
        assertThat(authorisationResponse.getStatus(), is(200));
        assertThat(authorisationResponse.getEntity(), is(Map.of("status", "AUTHORISATION 3DS REQUIRED")));
    }

    @Test
    void shouldReturn402_ifGatewayErrorsForGooglePay() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        WorldpayGooglePayAuthRequest worldpayGooglePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), WorldpayGooglePayAuthRequest.class);
        GatewayError gatewayError = mock(GatewayError.class);
        GatewayResponse gatewayResponse = responseBuilder()
                .withGatewayError(gatewayError)
                .withSessionIdentifier(ProviderSessionIdentifier.of("234"))
                .build();
        when(gatewayError.getErrorType()).thenReturn(GATEWAY_ERROR);
        when(gatewayError.getMessage()).thenReturn("oops");
        when(mockWalletAuthoriseService.authorise(externalChargeId, worldpayGooglePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, worldpayGooglePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, worldpayGooglePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse)authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("oops"));
    }

    @Test
    void shouldReturn402_ifResponseHasAuthorisationStatusError() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        WorldpayGooglePayAuthRequest worldpayGooglePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), WorldpayGooglePayAuthRequest.class);
        GatewayError gatewayError = mock(GatewayError.class);

        
        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.ERROR);
        when(mockWalletAuthoriseService.authorise(externalChargeId, worldpayGooglePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, worldpayGooglePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, worldpayGooglePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(402));
        ErrorResponse response = (ErrorResponse)authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("There was an error authorising the transaction."));
    }

    @Test
    void shouldReturn400_ifResponseHasAuthorisationStatusRejected() throws JsonProcessingException {
        String externalChargeId = "external-charge-id";
        WorldpayGooglePayAuthRequest worldpayGooglePayAuthRequest = Jackson.getObjectMapper().readValue(fixture("googlepay/example-auth-request.json"), WorldpayGooglePayAuthRequest.class);

        GatewayResponse gatewayResponse = responseBuilder()
                .withResponse(worldpayResponse)
                .build();
        when(worldpayResponse.authoriseStatus()).thenReturn(BaseAuthoriseResponse.AuthoriseStatus.REJECTED);
        when(mockWalletAuthoriseService.authorise(externalChargeId, worldpayGooglePayAuthRequest)).thenReturn(gatewayResponse);

        Response authorisationResponse = walletService.authorise(externalChargeId, worldpayGooglePayAuthRequest);

        verify(mockWalletAuthoriseService).authorise(externalChargeId, worldpayGooglePayAuthRequest);
        assertThat(authorisationResponse.getStatus(), is(400));
        ErrorResponse response = (ErrorResponse)authorisationResponse.getEntity();
        assertThat(response.getMessages(), contains("This transaction was declined."));
        assertThat(response.getIdentifier(), is(ErrorIdentifier.AUTHORISATION_REJECTED));
    }
}
