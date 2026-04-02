package uk.gov.pay.connector.client.ledger.service;

import au.com.dius.pact.consumer.dsl.PactDslJsonArray;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit.PactProviderRule;
import au.com.dius.pact.consumer.junit.PactVerification;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.pay.connector.agreement.model.AgreementEntity;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.RestClientFactory;
import uk.gov.pay.connector.app.config.RestClientConfig;
import uk.gov.pay.connector.client.ledger.model.LedgerTransaction;
import uk.gov.pay.connector.client.ledger.model.RefundTransactionsForPayment;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.agreement.AgreementCreated;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.agreement.model.AgreementEntity.AgreementEntityBuilder.anAgreementEntity;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.STRIPE;
import static uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntityFixture.aGatewayAccountEntity;

@RunWith(MockitoJUnitRunner.class)
public class LedgerServiceConsumerTest {

    public static final String TRANSACTION_ID = "e8eq11mi2ndmauvb51qsg8hccn";
    public static final String AGREEMENT_ID = "r4ou8b7fb52is55fp4iiav5bon";
    public static final String CREDENTIAL_ID = "credential-external-id";

    @Rule
    public PactProviderRule mockLedger = new PactProviderRule("ledger", this);

    @Mock
    ConnectorConfiguration configuration;

    private LedgerService ledgerService;

    @Before
    public void setUp() {
        when(configuration.getLedgerBaseUrl()).thenReturn(mockLedger.getUrl());
        Client client = RestClientFactory.buildClient(new RestClientConfig(), null);
        ledgerService = new LedgerService(client, client, configuration);
    }

    @Pact(consumer = "connector")
    public RequestResponsePact getRecurringPaymentTransaction(PactDslWithProvider builder) {
        return builder
                .given("a recurring card payment exists for agreement",
                        Map.of("account_id", 3,
                                "transaction_external_id", TRANSACTION_ID,
                                "agreement_id", AGREEMENT_ID))
                .uponReceiving("a recurring payment transaction request")
                .path("/v1/transaction/" + TRANSACTION_ID)
                .query("override_account_id_restriction=true")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringValue("gateway_account_id", "3")
                        .numberValue("amount", 12000)
                        .object("state", new PactDslJsonBody()
                                .stringValue("status", "success"))
                        .stringValue("description", "New passport application")
                        .stringValue("reference", "1_86")
                        .stringValue("language", "cy")
                        .stringValue("return_url", "https://service-name.gov.uk/transactions/12345")
                        .stringValue("payment_provider", "sandbox")
                        .stringValue("credential_external_id", CREDENTIAL_ID)
                        .stringValue("created_date", "2020-02-13T16:26:04.204Z")
                        .booleanValue("delayed_capture", false)
                        .stringValue("transaction_type", "PAYMENT")
                        .booleanValue("moto", false)
                        .booleanValue("live", false)
                        .stringValue("transaction_id", TRANSACTION_ID)
                        .booleanValue("disputed", false)
                        .stringValue("authorisation_mode", "agreement")
                        .stringValue("agreement_id", AGREEMENT_ID)
                        .stringValue("agreement_payment_type", "recurring"))
                .toPact();
    }

    @Test
    @PactVerification(fragment = "getRecurringPaymentTransaction")
    public void getTransaction_shouldSerialiseLedgerRecurringPaymentTransactionCorrectly() {
        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(TRANSACTION_ID);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getAgreementId(), is(AGREEMENT_ID));
    }

    @Pact(consumer = "connector")
    public RequestResponsePact getPaymentTransaction(PactDslWithProvider builder) {
        return builder
                .given("a payment transaction exists",
                        Map.of("gateway_account_id", "3",
                                "transaction_external_id", TRANSACTION_ID,
                                "cardholder_name", "John Doe"))
                .uponReceiving("a payment transaction request")
                .path("/v1/transaction/" + TRANSACTION_ID)
                .query("override_account_id_restriction=true")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringValue("gateway_account_id", "3")
                        .numberValue("amount", 12000)
                        .object("state", new PactDslJsonBody()
                                .stringValue("status", "success"))
                        .stringValue("description", "New passport application")
                        .stringValue("reference", "1_86")
                        .stringValue("language", "cy")
                        .stringValue("return_url", "https://service-name.gov.uk/transactions/12345")
                        .stringValue("payment_provider", "sandbox")
                        .stringValue("credential_external_id", CREDENTIAL_ID)
                        .stringValue("created_date", "2020-02-13T16:26:04.204Z")
                        .booleanValue("delayed_capture", false)
                        .stringValue("transaction_type", "PAYMENT")
                        .booleanValue("moto", false)
                        .booleanValue("live", false)
                        .stringValue("transaction_id", TRANSACTION_ID)
                        .booleanValue("disputed", false)
                        .stringValue("authorisation_mode", "web"))
                .toPact();
    }

    @Test
    @PactVerification(fragment = "getPaymentTransaction")
    public void getTransaction_shouldSerialiseLedgerPaymentTransactionCorrectly() {
        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(TRANSACTION_ID);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getTransactionId(), is(TRANSACTION_ID));
        assertThat(transaction.getAmount(), is(12000L));
        assertThat(transaction.getGatewayAccountId(), is("3"));
        assertThat(transaction.getCredentialExternalId(), is(CREDENTIAL_ID));
    }

    @Pact(consumer = "connector")
    public RequestResponsePact getRefundTransaction(PactDslWithProvider builder) {
        return builder
                .given("a refund transaction for a transaction exists",
                        Map.of("gateway_account_id", "3",
                                "transaction_external_id", TRANSACTION_ID,
                                "parent_external_id", "64pcdagc9c13vgi7n904aio3n9"))
                .uponReceiving("a refund transaction request")
                .path("/v1/transaction/" + TRANSACTION_ID)
                .query("override_account_id_restriction=true")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringValue("gateway_account_id", "3")
                        .numberValue("amount", 1000)
                        .object("state", new PactDslJsonBody()
                                .stringValue("status", "success"))
                        .stringValue("created_date", "2020-07-20T13:39:38.940Z")
                        .stringValue("transaction_type", "REFUND")
                        .stringValue("transaction_id", TRANSACTION_ID)
                        .stringValue("parent_transaction_id", "64pcdagc9c13vgi7n904aio3n9"))
                .toPact();
    }

    @Test
    @PactVerification(fragment = "getRefundTransaction")
    public void getTransaction_shouldSerialiseLedgerRefundTransactionCorrectly() {
        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransaction(TRANSACTION_ID);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getTransactionId(), is(TRANSACTION_ID));
        assertThat(transaction.getAmount(), is(1000L));
        assertThat(transaction.getParentTransactionId(), is("64pcdagc9c13vgi7n904aio3n9"));
        assertThat(transaction.getCreatedDate(), is("2020-07-20T13:39:38.940Z"));
        assertThat(transaction.getState().getStatus(), is("success"));
    }

    @Pact(consumer = "connector")
    public RequestResponsePact getRefundsForPayment(PactDslWithProvider builder) {
        return builder
                .given("refund and dispute transactions for a transaction exist",
                        Map.of("gateway_account_id", "3",
                                "transaction_external_id", TRANSACTION_ID))
                .uponReceiving("a refund transaction request")
                .path("/v1/transaction/" + TRANSACTION_ID + "/transaction")
                .query("gateway_account_id=3&transaction_type=REFUND")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringValue("parent_transaction_id", TRANSACTION_ID)
                        .array("transactions")

                        .object()
                        .stringValue("gateway_account_id", "3")
                        .numberValue("amount", 100)
                        .object("state", new PactDslJsonBody().stringValue("status", "submitted"))
                        .stringValue("created_date", "2019-12-23T15:24:07.061Z")
                        .stringValue("transaction_type", "REFUND")
                        .stringValue("transaction_id", "nklfm1pk9flpu91j815kp2835o")
                        .stringValue("parent_transaction_id", "650516the13q5jpfo435f1m1fm")
                        .closeObject()

                        .object()
                        .stringValue("gateway_account_id", "3")
                        .numberValue("amount", 110)
                        .object("state", new PactDslJsonBody().stringValue("status", "error"))
                        .stringValue("created_date", "2019-12-23T16:20:12.343Z")
                        .stringValue("transaction_type", "REFUND")
                        .stringValue("transaction_id", "migtkmlt6gvm16sim5h0p7oeje")
                        .stringValue("parent_transaction_id", "650516the13q5jpfo435f1m1fm")
                        .closeObject()

                        .closeArray()
                )
                .toPact();
    }

    @Test
    @PactVerification(fragment = "getRefundsForPayment")
    public void getRefundsFromLedgerShouldSerialiseResponseCorrectly() {
        RefundTransactionsForPayment refundTransactionsForPayment =
                ledgerService.getRefundsForPayment(3L, TRANSACTION_ID);

        assertThat(refundTransactionsForPayment.getParentTransactionId(), is(TRANSACTION_ID));

        List<LedgerTransaction> transactions = refundTransactionsForPayment.getTransactions();

        assertThat(transactions.getFirst().getTransactionId(), is("nklfm1pk9flpu91j815kp2835o"));
        assertThat(transactions.getFirst().getGatewayAccountId(), is("3"));
        assertThat(transactions.getFirst().getAmount(), is(100L));
        assertThat(transactions.getFirst().getState().getStatus(), is("submitted"));

        assertThat(transactions.get(1).getTransactionId(), is("migtkmlt6gvm16sim5h0p7oeje"));
        assertThat(transactions.get(1).getAmount(), is(110L));
        assertThat(transactions.get(1).getGatewayAccountId(), is("3"));
        assertThat(transactions.get(1).getState().getStatus(), is("error"));
    }

    @Pact(consumer = "connector")
    public RequestResponsePact postEventSuccessful(PactDslWithProvider builder) {
        return builder
                .uponReceiving("an agreement created event")
                .path("/v1/event")
                .method("POST")
                .headers("Content-Type", "application/json")
                .body(new PactDslJsonArray()
                        .object()
                        .stringValue("resource_external_id", "ureehr17f66a9ds1bg3heqkkhk")
                        .object("event_details", new PactDslJsonBody()
                                .stringValue("gateway_account_id", "3456")
                                .stringValue("reference", "agreement created post event")
                                .stringValue("description", "a valid description")
                                .stringValue("user_identifier", "a-valid-user-identifier"))
                        .stringValue("timestamp", "2023-06-27T11:23:30.000000Z")
                        .stringValue("service_id", "a-valid-service-id")
                        .booleanValue("live", false)
                        .stringValue("resource_type", "agreement")
                        .stringValue("event_type", "AGREEMENT_CREATED")
                        .closeObject())
                .willRespondWith()
                .status(202)
                .toPact();
    }

    @Test
    @PactVerification(fragment = "postEventSuccessful")
    public void postAgreementCreatedEvent() {
        AgreementEntity agreementEntity = anAgreementEntity(Instant.parse("2023-06-27T11:23:30.000000Z"))
                .withReference("agreement created post event")
                .withDescription("a valid description")
                .withUserIdentifier("a-valid-user-identifier")
                .withServiceId("a-valid-service-id")
                .withLive(false)
                .build();
        agreementEntity.setExternalId("ureehr17f66a9ds1bg3heqkkhk");
        GatewayAccountEntity gatewayAccountEntity = aGatewayAccountEntity()
                .withGatewayName(STRIPE.getName())
                .withId(3456L)
                .build();
        agreementEntity.setGatewayAccount(gatewayAccountEntity);
        Event event = AgreementCreated.from(agreementEntity);
        Response response = ledgerService.postEvent(event);

        assertThat(response.getStatus(), is(202));
    }

    @Pact(consumer = "connector")
    public RequestResponsePact getPaymentTransactionByGatewayTransactionId(PactDslWithProvider builder) {
        return builder
                .given("a transaction with a gateway transaction id exists",
                        Map.of("account_id", "123456",
                                "charge_id", "ch_123abc456xyz",
                                "gateway_transaction_id", "gateway-tx-123456"))
                .uponReceiving("a payment transaction by gateway transaction id request")
                .path("/v1/transaction/gateway-transaction")
                .query("gateway_transaction_id=gateway-tx-123456&payment_provider=sandbox")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .numberValue("amount", 100)
                        .object("state", new PactDslJsonBody()
                                .booleanValue("finished", false)
                                .stringValue("status", "created"))
                        .stringValue("description", "Test description")
                        .stringValue("reference", "aReference")
                        .stringValue("language", "en")
                        .stringValue("transaction_id", "ch_123abc456xyz")
                        .stringValue("gateway_transaction_id", "gateway-tx-123456")
                        .stringValue("return_url", "https://somewhere.gov.uk/rainbow/1")
                        .stringValue("payment_provider", "sandbox")
                        .stringValue("created_date", "2018-10-16T10:46:02.121Z")
                        .object("refund_summary", new PactDslJsonBody()
                                .stringValue("status", "available")
                                .nullValue("user_external_id")
                                .numberValue("amount_available", 100)
                                .numberValue("amount_submitted", 0))
                        .object("settlement_summary", new PactDslJsonBody()
                                .nullValue("capture_submit_time")
                                .nullValue("capture_date"))
                        .booleanValue("delayed_capture", false))
                .toPact();
    }

    @Test
    @PactVerification(fragment = "getPaymentTransactionByGatewayTransactionId")
    public void getTransactionByGatewayTransactionId_shouldSerialiseLedgerPaymentTransactionCorrectly() {
        String gatewayTransactionId = "gateway-tx-123456";
        String paymentProvider = "sandbox";

        Optional<LedgerTransaction> mayBeTransaction = ledgerService.getTransactionForProviderAndGatewayTransactionId(paymentProvider, gatewayTransactionId);

        assertThat(mayBeTransaction.isPresent(), is(true));
        LedgerTransaction transaction = mayBeTransaction.get();
        assertThat(transaction.getGatewayTransactionId(), is(gatewayTransactionId));
        assertThat(transaction.getAmount(), is(100L));
    }
}
