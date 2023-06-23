package uk.gov.pay.connector.gateway.worldpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.gateway.GatewayClient;
import uk.gov.pay.connector.gateway.GatewayException;
import uk.gov.pay.connector.gateway.GatewayOrder;
import uk.gov.pay.connector.gateway.worldpay.exception.UnexpectedValidateCredentialsResponse;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.WorldpayValidatableCredentials;

import java.net.URI;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.WORLDPAY;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType.TEST;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_INVALID_MERCHANT_ID_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_UNEXPECTED_ERROR_CODE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_VALID_RESPONSE;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

@ExtendWith(MockitoExtension.class)
class WorldpayValidatableCredentialsValidationServiceTest {

    private static final URI WORLDPAY_URL = URI.create("http://worldpay.url");
    private static final Map<String, URI> GATEWAY_URL_MAP = Map.of(TEST.toString(), WORLDPAY_URL);

    @Mock
    GatewayClient gatewayClient;

    @Mock
    private GatewayClient.Response response;

    private WorldpayCredentialsValidationService worldpayCredentialsValidationService;

    private final GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
            .withGatewayName(STRIPE.getName())
            .withType(TEST)
            .build();

    private final WorldpayValidatableCredentials worldpayValidatableCredentials = new WorldpayValidatableCredentials("A_MERCHANT_ID", "user123", "test");

    @BeforeEach
    void setUp() {
        worldpayCredentialsValidationService = new WorldpayCredentialsValidationService(
                GATEWAY_URL_MAP,
                gatewayClient);
    }

    @Test
    void shouldReturnTrue_ifWorldpayRespondsWithStatus200AndErrorCode5() throws GatewayException {
        when(response.getEntity()).thenReturn(load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_VALID_RESPONSE));

        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenReturn(response);

        boolean valid = worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials);
        assertThat(valid, is(true));
    }

    @Test
    void shouldReturnFalse_ifWorldpayRespondsWithStatus200AndErrorCode4() throws GatewayException {
        when(response.getEntity()).thenReturn(load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_INVALID_MERCHANT_ID_RESPONSE));

        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenReturn(response);

        boolean valid = worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials);
        assertThat(valid, is(false));
    }

    @Test
    void shouldReturnFalse_ifWorldpayRespondsWithStatus401() throws GatewayException {
        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Error", "Error", 401));

        boolean valid = worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials);
        assertThat(valid, is(false));
    }

    @Test
    void shouldThrowException_whenErrorCodeUnexpected() throws GatewayException {
        when(response.getEntity()).thenReturn(load(WORLDPAY_INQUIRY_CREDENTIAL_VALIDATION_UNEXPECTED_ERROR_CODE));

        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenReturn(response);

        assertThrows(UnexpectedValidateCredentialsResponse.class, () -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials));
    }

    @Test
    void shouldThrowException_whenErrorStatusCodeNot401() throws GatewayException {
        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenThrow(new GatewayException.GatewayErrorException("Error", "Error", 403));

        assertThrows(UnexpectedValidateCredentialsResponse.class, () -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials));
    }

    @Test
    void shouldWrapGatewayException() throws GatewayException {
        when(gatewayClient.postRequestFor(eq(WORLDPAY_URL), eq(WORLDPAY), eq("test"), any(GatewayOrder.class), eq(emptyList()), anyMap()))
                .thenThrow(GatewayException.GenericGatewayException.class);

        assertThrows(UnexpectedValidateCredentialsResponse.class, () -> worldpayCredentialsValidationService.validateCredentials(gatewayAccountEntity, worldpayValidatableCredentials));
    }
}
