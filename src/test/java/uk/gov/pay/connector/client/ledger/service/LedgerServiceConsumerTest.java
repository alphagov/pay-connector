package uk.gov.pay.connector.client.ledger.service;

import au.com.dius.pact.consumer.PactVerification;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.service.payments.commons.testing.pact.consumers.PactProviderRule;
import uk.gov.service.payments.commons.testing.pact.consumers.Pacts;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.RestClientFactory;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;

import javax.ws.rs.client.Client;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LedgerServiceConsumerTest {

    @Rule
    public PactProviderRule ledgerRule = new PactProviderRule("ledger", this);

    @Mock
    ConnectorConfiguration configuration;
    
    private LedgerService ledgerService;

    @Before
    public void setUp() {
        when(configuration.getLedgerBaseUrl()).thenReturn(ledgerRule.getUrl());
        Client client = RestClientFactory.buildClient(new RestClientConfig(), null);
        ledgerService = new LedgerService(client, client, configuration);
    }

    @Test
    @PactVerification("ledger")
    @Pacts(pacts = {"connector-ledger-get-payment-transaction"})
    public void getTransaction_shouldSerialiseLedgerPaymentTransactionCorrectly() {
        String externalId = "e8eq11mi2ndmauvb51qsg8hccn";
        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(externalId);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getTransactionId(), is(externalId));
        assertThat(transaction.getAmount(), is(12000L));
        assertThat(transaction.getGatewayAccountId(), is("3"));
        assertThat(transaction.getCredentialExternalId(), is("credential-external-id"));
    }

    @Test
    @PactVerification("ledger")
    @Pacts(pacts = {"connector-ledger-get-refund-transaction"})
    public void getTransaction_shouldSerialiseLedgerRefundTransactionCorrectly() {
        String externalId = "nklfm1pk9flpu91j815kp2835o";
        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(externalId);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getTransactionId(), is(externalId));
        assertThat(transaction.getAmount(), is(1000L));
        assertThat(transaction.getParentTransactionId(), is("64pcdagc9c13vgi7n904aio3n9"));
        assertThat(transaction.getCreatedDate(), is("2020-07-20T13:39:38.940Z"));
        assertThat(transaction.getState().getStatus(), is("success"));
    }

    @Test
    @PactVerification("ledger")
    @Pacts(pacts = {"connector-ledger-get-refunds-for-payment"})
    public void getRefundsFromLedgerShouldSerialiseResponseCorrectly() {
        String externalId = "650516the13q5jpfo435f1m1fm";
        RefundTransactionsForPayment refundTransactionsForPayment =
                ledgerService.getRefundsForPayment(3L, externalId);

        assertThat(refundTransactionsForPayment.getParentTransactionId(), is(externalId));

        List<LedgerTransaction> transactions = refundTransactionsForPayment.getTransactions();

        assertThat(transactions.get(0).getTransactionId(), is("nklfm1pk9flpu91j815kp2835o"));
        assertThat(transactions.get(0).getGatewayAccountId(), is("3"));
        assertThat(transactions.get(0).getAmount(), is(100L));
        assertThat(transactions.get(0).getState().getStatus(), is("submitted"));

        assertThat(transactions.get(1).getTransactionId(), is("migtkmlt6gvm16sim5h0p7oeje"));
        assertThat(transactions.get(1).getAmount(), is(110L));
        assertThat(transactions.get(1).getGatewayAccountId(), is("3"));
        assertThat(transactions.get(1).getState().getStatus(), is("error"));
    }
}
