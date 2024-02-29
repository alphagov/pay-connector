package uk.gov.pay.connector.paymentprocessor.service;

import io.dropwizard.setup.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.gateway.model.response.GatewayResponse;
import uk.gov.pay.connector.gateway.worldpay.WorldpayOrderStatusResponse;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.gateway.model.response.GatewayResponse.GatewayResponseBuilder.responseBuilder;

@ExtendWith(MockitoExtension.class)
class AuthorisationServiceTest {
    private final static String TRANSACTION_ID = "transaction_id";
    private final static String CURRENT_TRANSACTION_ID = "current_transaction_id";
    @Mock
    CardExecutorService executorService;
    @Mock
    Environment environment;
    @Mock
    ConnectorConfiguration config;

    AuthorisationService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthorisationService(executorService, environment, config);
    }

    @Test
    void  extractTransactionIdShouldReturnTransactionIdFromAuthResponse() {
        GatewayResponse authResponse = mockAuthResponse(TRANSACTION_ID);
        Optional<String> optionalTransactionId = authService.extractTransactionId("externalId", authResponse, CURRENT_TRANSACTION_ID);
        assertThat(optionalTransactionId.isPresent(), is(true));
        String transactionId = optionalTransactionId.get();
        assertThat(transactionId, is(TRANSACTION_ID));
    }

    @Test
    void extractTransactionIdShouldReturnCurrentTransactionIdIfResponseDoesnotHaveATransactionId() {
        GatewayResponse authResponse = mockAuthResponse(null);
        Optional<String> optionalTransactionId = authService.extractTransactionId("externalId", authResponse, CURRENT_TRANSACTION_ID);
        assertThat(optionalTransactionId.isPresent(), is(true));
        String transactionId = optionalTransactionId.get();
        assertThat(transactionId, is(CURRENT_TRANSACTION_ID));
    }

    @Test
    void extractTransactionIdShouldNotReturnTransactionId_whenNonPresent() {
        GatewayResponse authResponse = mockAuthResponse(null);
        Optional<String> optionalTransactionId = authService.extractTransactionId("externalId", authResponse, null);
        assertThat(optionalTransactionId.isPresent(), is(false));
    }

    private GatewayResponse mockAuthResponse(String transactionId) {
        WorldpayOrderStatusResponse worldpayResponse = mock(WorldpayOrderStatusResponse.class);
        when(worldpayResponse.getTransactionId()).thenReturn(transactionId);
        return responseBuilder()
                .withResponse(worldpayResponse)
                .build();
    }
}
