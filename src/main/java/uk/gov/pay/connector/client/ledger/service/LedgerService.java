package uk.gov.pay.connector.client.ledger.service;

import com.google.inject.name.Named;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.service.payments.logging.LoggingKeys.GATEWAY_ACCOUNT_ID;
import static uk.gov.service.payments.logging.LoggingKeys.HTTP_STATUS;
import static uk.gov.service.payments.logging.LoggingKeys.PAYMENT_EXTERNAL_ID;
import static uk.gov.service.payments.logging.LoggingKeys.URL;

public class LedgerService {

    private final Logger logger = LoggerFactory.getLogger(LedgerService.class);

    private final Client client;
    private final Client postEventClient;
    private final String ledgerUrl;
    private final UriBuilder eventUri;

    @Inject
    public LedgerService(Client client, @Named("ledgerClient") Client ledgerClient, ConnectorConfiguration configuration) {
        this.ledgerUrl = configuration.getLedgerBaseUrl();
        this.eventUri = UriBuilder.fromPath(this.ledgerUrl).path("/v1/event");
        this.client = client;
        this.postEventClient = ledgerClient;
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
                .path(format("/v1/transaction/gateway-transaction/%s", URLEncoder.encode(gatewayTransactionId, StandardCharsets.UTF_8)))
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
                .queryParam("gateway_account_id", gatewayAccountId)
                .queryParam("transaction_type", "REFUND");

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
        return postEvents(List.of(event));
    }
    
    public Response postEvent(List<Event> events) {
        return postEvents(events);
    }
    
    private Response postEvents(List<Event> events) {
        String eventsList = events.stream().map(Event::getEventType).collect(Collectors.joining(", "));
        logger.info("Making POST request to send events to ledger for: [" + eventsList + "]", 
                kv("events", events.stream().map(Event::toString).collect(Collectors.toList())));
        var response = postEventClient.target(eventUri)
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
        if (response.getStatus() == SC_NOT_FOUND) {
            return Optional.empty();
        }
        logger.error("Received error status code for GET transaction from ledger.", 
                kv(URL, uri),
                kv(HTTP_STATUS, response.getStatus()));
        throw new LedgerException(response);
    }

    private Response getResponse(UriBuilder uri) {
        return client
                .target(uri)
                .request()
                .get();
    }
}
