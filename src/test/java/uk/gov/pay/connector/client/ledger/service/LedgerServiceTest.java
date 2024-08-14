package uk.gov.pay.connector.client.ledger.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.agreement.AgreementCreated;
import uk.gov.pay.connector.events.model.agreement.AgreementInactivated;
import uk.gov.pay.connector.events.model.agreement.AgreementSetUp;
import uk.gov.pay.connector.gateway.model.MappedAuthorisationRejectedReason;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentStatus;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;
import static uk.gov.pay.connector.model.domain.LedgerTransactionFixture.aValidLedgerTransaction;
import static uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity.PaymentInstrumentEntityBuilder.aPaymentInstrumentEntity;

@ExtendWith(MockitoExtension.class)
public class LedgerServiceTest {

    @Mock
    private Client mockClient;
    @Mock
    private ConnectorConfiguration mockConnectorConfiguration;
    @Mock
    private WebTarget mockWebTarget;
    @Mock
    private Invocation.Builder mockClientRequestInvocationBuilder;
    @Mock
    private Response mockResponse;

    private LedgerService ledgerService;

    @BeforeEach
    void setUp() {
        when(mockConnectorConfiguration.getLedgerBaseUrl()).thenReturn("http://ledgerUrl");
        when(mockClient.target(any(UriBuilder.class))).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockClientRequestInvocationBuilder);
        
        ledgerService = new LedgerService(mockClient, mockClient, mockConnectorConfiguration);
    }

    private void setupMocksForPostRequest() {
        when(mockClientRequestInvocationBuilder.accept(APPLICATION_JSON)).thenReturn(mockClientRequestInvocationBuilder);
        when(mockClientRequestInvocationBuilder.post(any(Entity.class))).thenReturn(mockResponse);
    }

    private void setupMocksForGetRequest() {
        when(mockClientRequestInvocationBuilder.get()).thenReturn(mockResponse);
    }

    @Test
    void getRefundsFromLedgerShouldThrowExceptionForNon2xxResponse() {
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_NOT_FOUND);

        assertThrows(GetRefundsForPaymentException.class, () -> ledgerService.getRefundsForPayment(152L, "external-id"));
    }

    @Test
    void getRefundsFromLedgerShouldThrowExceptionIfResponseCannotBeProcessed() {
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_OK);
        when(mockResponse.readEntity(RefundTransactionsForPayment.class)).
                thenThrow(ProcessingException.class);

        assertThrows(LedgerException.class, () -> ledgerService.getRefundsForPayment(152L, "external-id"));
    }

    @Test
    void serialiseAndSendEvent() {
        setupMocksForPostRequest();
        var event = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        when(mockResponse.getStatus()).thenReturn(SC_ACCEPTED);
        ledgerService.postEvent(event);
        verify(mockClientRequestInvocationBuilder).post(Entity.json(List.of(event)));
    }

    @Test
    void sendEventShouldThrowExceptionIfResponseIsNotAccepted() {
        setupMocksForPostRequest();
        var event = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        when(mockResponse.getStatus()).thenReturn(SC_BAD_REQUEST);
        assertThrows(LedgerException.class, () -> ledgerService.postEvent(event));
    }

    @Test
    void serialiseAndSendMultipleEvents() {
        setupMocksForPostRequest();
        var eventOne = new AgreementCreated("service-id", false, "resource-id", null, Instant.now());
        var eventTwo = new AgreementSetUp("service-id", false, "resource-id", null, Instant.now());
        PaymentInstrumentEntity paymentInstrumentEntity = aPaymentInstrumentEntity(Instant.now())
                .withStatus(PaymentInstrumentStatus.ACTIVE)
                .build();
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity().build();
        AgreementEntity agreementEntity = anAgreementEntity(Instant.now())
                .withReference("This is the reference")
                .withDescription("This is a description")
                .withUserIdentifier("This is the user identifier")
                .withPaymentInstrument(paymentInstrumentEntity)
                .withGatewayAccount(gatewayAccountEntity)
                .build();
        var eventThree = AgreementInactivated.from(agreementEntity, MappedAuthorisationRejectedReason.EXPIRED_CARD.name(), Instant.now());

        List<Event> list = List.of(eventOne, eventTwo, eventThree);
        when(mockResponse.getStatus()).thenReturn(SC_ACCEPTED);
        ledgerService.postEvent(list);
        verify(mockClientRequestInvocationBuilder).post(Entity.json(list));
    }

    @Test
    void shouldReturnTransactionWhenLedgerReturnsSuccessResponse() {
        LedgerTransaction ledgerTransaction = aValidLedgerTransaction().build();
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_OK);
        when(mockResponse.readEntity(LedgerTransaction.class)).thenReturn(ledgerTransaction);

        Optional<LedgerTransaction> maybeTransaction = ledgerService.getTransaction("transaction-id");
        assertThat(maybeTransaction.isPresent(), is(true));
        assertThat(maybeTransaction.get(), is(ledgerTransaction));
    }

    @Test
    void getTransaction_shouldReturnEmptyOptionalWhenLedgerReturns404() {
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_NOT_FOUND);

        assertThat(ledgerService.getTransaction("transaction-id").isEmpty(), is(true));
    }

    @Test
    void getTransaction_shouldThrowExceptionWhenLedgerReturns500() {
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_INTERNAL_SERVER_ERROR);

        assertThrows(LedgerException.class, () -> ledgerService.getTransaction("transaction-id"));
    }

    @Test
    void getTransactionForProviderAndGatewayTransactionId_shouldEncodeCorrectly() {
        var oddId = "an-id-with//some-weird-stuff-in-it";
        LedgerTransaction ledgerTransaction = aValidLedgerTransaction()
                .withPaymentProvider("worldpay")
                .withGatewayTransactionId(oddId)
                .build();
        setupMocksForGetRequest();
        when(mockResponse.getStatus()).thenReturn(SC_OK);
        when(mockResponse.readEntity(LedgerTransaction.class)).thenReturn(ledgerTransaction);

        var expectedPath = "/v1/transaction/gateway-transaction/an-id-with%2F%2Fsome-weird-stuff-in-it";
        
        Optional<LedgerTransaction> maybeTransaction = ledgerService.getTransactionForProviderAndGatewayTransactionId("worldpay", oddId);
        verify(mockClient).target(argThat((UriBuilder arg) -> {
            assertEquals(expectedPath, arg.build().getRawPath());
            return true;
        }));
        assertThat(maybeTransaction.isPresent(), is(true));
        assertThat(maybeTransaction.get(), is(ledgerTransaction));
    }
}
