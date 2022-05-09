package uk.gov.pay.connector.client.ledger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.client.ledger.exception.GetRefundsForPaymentException;
import uk.gov.pay.connector.client.ledger.exception.LedgerException;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.events.model.Event;

import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;

public class LedgerService {

    private final Logger logger = LoggerFactory.getLogger(LedgerService.class);

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

        return getTransactionFromLedger(uri);
    }

    public Optional<LedgerTransaction> getTransactionForProviderAndGatewayTransactionId(String paymentGatewayName,
                                                                                        String gatewayTransactionId) {
        var uri = UriBuilder
                .fromPath(ledgerUrl)
                .path(format("/v1/transaction/gateway-transaction/%s", gatewayTransactionId))
                .queryParam("payment_provider", paymentGatewayName);

        return getTransactionFromLedger(uri);
    }

    public Optional<LedgerTransaction> getTransactionForGatewayAccount(String id, Long gatewayAccountId) {
        var uri = UriBuilder
                .fromPath(ledgerUrl)
                .path(format("/v1/transaction/%s", id))
                .queryParam("account_id", gatewayAccountId);

        return getTransactionFromLedger(uri);
    }

    public RefundTransactionsForPayment getRefundsForPayment(Long gatewayAccountId, String paymentExternalId) {
        var uri = UriBuilder
                .fromPath(ledgerUrl)
                .path(format("/v1/transaction/%s/transaction", paymentExternalId))
                .queryParam("gateway_account_id", gatewayAccountId);

        Response response = getResponse(uri);

        if (response.getStatus() == SC_OK) {
            try {
                return response.readEntity(RefundTransactionsForPayment.class);
            } catch (ProcessingException exception) {
                logger.error("Error processing response from ledger for payment refunds: {} {}",
                        kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                        kv(PAYMENT_EXTERNAL_ID, paymentExternalId));
                throw new LedgerException(exception);
            }
        } else {
            logger.error("Received non-success status code for get refunds for payment from Ledger: {}, {}",
                    kv(GATEWAY_ACCOUNT_ID, gatewayAccountId),
                    kv(PAYMENT_EXTERNAL_ID, paymentExternalId));
            throw new GetRefundsForPaymentException(response);
        }
    }

    public Response postEvent(Event event) {
        return postEventList(List.of(event)); 
    }
    
    public Response postEvent(List<Event> events) {
        return postEventList(events);
    }
    
    private Response postEventList(List<Event> events) {
        var uri = UriBuilder.fromPath(ledgerUrl).path("/v1/event");
        var response = client.target(uri)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json(events));

        if (response.getStatus() == SC_ACCEPTED) {
            return response;
        } else {
            throw new LedgerException(response);
        }
    }

    private Optional<LedgerTransaction> getTransactionFromLedger(UriBuilder uri) {
        Response response = getResponse(uri);

        if (response.getStatus() == SC_OK) {
            return Optional.of(response.readEntity(LedgerTransaction.class));
        }

        return Optional.empty();
    }

    private Response getResponse(UriBuilder uri) {
        return client
                .target(uri)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
    }

}
