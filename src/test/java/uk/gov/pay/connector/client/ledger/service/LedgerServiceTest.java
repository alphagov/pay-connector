package uk.gov.pay.connector.client.ledger.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.charge.AgreementCreated;
import uk.gov.pay.connector.events.model.charge.AgreementSetup;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LedgerServiceTest {
    private LedgerService ledgerService;
    private Response mockResponse;
    private Invocation.Builder mockClientRequestInvocationBuilder;

    @Before
    public void setUp() {
        Client mockClient = mock(Client.class);
        ConnectorConfiguration mockConnectorConfiguration = mock(ConnectorConfiguration.class);
        WebTarget mockWebTarget = mock(WebTarget.class);
        mockClientRequestInvocationBuilder = mock(Invocation.Builder.class);
        mockResponse = mock(Response.class);

        when(mockConnectorConfiguration.getLedgerBaseUrl()).thenReturn("http://ledgerUrl");
        when(mockClient.target(any(UriBuilder.class))).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockClientRequestInvocationBuilder);
        when(mockClientRequestInvocationBuilder.accept(APPLICATION_JSON)).thenReturn(mockClientRequestInvocationBuilder);
        when(mockClientRequestInvocationBuilder.post(any(Entity.class))).thenReturn(mockResponse);
        when(mockClientRequestInvocationBuilder.get()).thenReturn(mockResponse);

        when(mockResponse.getStatus()).thenReturn(SC_OK);
        ledgerService = new LedgerService(mockClient, mockClient, mockConnectorConfiguration);
    }

    @Test(expected = GetRefundsForPaymentException.class)
    public void getRefundsFromLedgerShouldThrowExceptionForNon2xxResponse() {
        when(mockResponse.getStatus()).thenReturn(SC_NOT_FOUND);

        ledgerService.getRefundsForPayment(152L, "external-id");
    }

    @Test(expected = LedgerException.class)
    public void getRefundsFromLedgerShouldThrowExceptionIfResponseCannotBeProcessed() {
        when(mockResponse.readEntity(RefundTransactionsForPayment.class)).
                thenThrow(ProcessingException.class);

        ledgerService.getRefundsForPayment(152L, "external-id");
    }

    @Test
    public void serialiseAndSendEvent() {
        var event = new AgreementCreated("service-id", false, "resource-id", null, ZonedDateTime.now(ZoneOffset.UTC));
        when(mockResponse.getStatus()).thenReturn(SC_ACCEPTED);
        ledgerService.postEvent(event);
        verify(mockClientRequestInvocationBuilder).post(Entity.json(List.of(event)));
    }

    @Test(expected = LedgerException.class)
    public void sendEventShouldThrowExceptionIfResponseIsNotAccepted() {
        var event = new AgreementCreated("service-id", false, "resource-id", null, ZonedDateTime.now(ZoneOffset.UTC));
        when(mockResponse.getStatus()).thenReturn(SC_BAD_REQUEST);
        ledgerService.postEvent(event);
    }
    
    @Test
    public void serialiseAndSendMultipleEvents() {
        var eventOne = new AgreementCreated("service-id", false, "resource-id", null, ZonedDateTime.now(ZoneOffset.UTC));
        var eventTwo = new AgreementSetup("service-id", false, "resource-id", null, ZonedDateTime.now(ZoneOffset.UTC));
        List<Event> list = List.of(eventOne, eventTwo);
        when(mockResponse.getStatus()).thenReturn(SC_ACCEPTED);
        ledgerService.postEvent(list);
        verify(mockClientRequestInvocationBuilder).post(Entity.json(list));
    }
}
