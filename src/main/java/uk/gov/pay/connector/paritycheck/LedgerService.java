package uk.gov.pay.connector.paritycheck;

import uk.gov.pay.connector.app.ConnectorConfiguration;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static org.apache.http.HttpStatus.SC_OK;

public class LedgerService {

    private final Client client;
    private final String ledgerUrl;

    @Inject
    public LedgerService(Client client, ConnectorConfiguration configuration) {
        this.client = client;
        this.ledgerUrl = configuration.getLedgerBaseUrl();
    }

    public Optional<LedgerTransaction> getTransaction(String id) {
        var uri = UriBuilder
                .fromPath(ledgerUrl)
                .path(format("/v1/transaction/%s", id))
                .queryParam("override_account_id_restriction", "true");

        Response response = client
                .target(uri)
                .request()
                .get();

        if (response.getStatus() == SC_OK) {
            return Optional.of(response.readEntity(LedgerTransaction.class));
        }

        return Optional.empty();
    }

    public List<LedgerTransaction> getChildTransaction(String id, Long accountId) {
        var uri = UriBuilder
                .fromPath(ledgerUrl)
                .path(format("/v1/transaction/%s/transaction", id))
                .queryParam("gateway_account_id", accountId);

        Response response = client
                .target(uri)
                .request()
                .get();

        if (response.getStatus() == SC_OK) {
            return response.readEntity(LedgerTransactionResponse.class).getTransactions();  
        }

        return List.of();   //probably better way
    }
}
