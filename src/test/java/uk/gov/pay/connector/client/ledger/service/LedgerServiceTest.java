package uk.gov.pay.connector.client.ledger.service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LedgerServiceTest {
    
    private LedgerService ledgerService;
    private Response mockResponse;

    @Before
    public void setUp() {
        Client mockClient = mock(Client.class);
        ConnectorConfiguration mockConnectorConfiguration = mock(ConnectorConfiguration.class);
        WebTarget mockWebTarget = mock(WebTarget.class);
        Invocation.Builder mockBuilder = mock(Invocation.Builder.class);
        mockResponse = mock(Response.class);

        when(mockConnectorConfiguration.getLedgerBaseUrl()).thenReturn("http://ledgerUrl");
        when(mockClient.target(any(UriBuilder.class))).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockBuilder);
        when(mockBuilder.accept(APPLICATION_JSON)).thenReturn(mockBuilder);
        when(mockBuilder.get()).thenReturn(mockResponse);

        when(mockResponse.getStatus()).thenReturn(SC_OK);
        ledgerService = new LedgerService(mockClient, mockConnectorConfiguration);
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
}
