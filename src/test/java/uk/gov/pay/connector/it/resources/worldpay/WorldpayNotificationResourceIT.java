package uk.gov.pay.connector.it.resources.worldpay;

import io.dropwizard.setup.Environment;
import io.restassured.response.ValidatableResponse;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.app.ConnectorConfiguration;
import uk.gov.pay.connector.app.ConnectorModule;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.ConfigOverride;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DnsPointerResourceRecord;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.ReverseDnsLookup;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(
        app = WorldpayNotificationResourceIT.ConnectorAppWithCustomInjector.class, 
        config = "config/test-it-config.yaml",
        configOverrides = {@ConfigOverride(key = "worldpay.notificationDomain", value = ".worldpay.com")}
)
public class WorldpayNotificationResourceIT extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";
    private static final String WORLDPAY_IP_ADDRESS = "some-worldpay-ip";
    private static final String UNEXPECTED_IP_ADDRESS = "8.8.8.8";
    private static ReverseDnsLookup reverseDnsLookup = mock(ReverseDnsLookup.class);
    
    @BeforeClass
    public static void before() {
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(WORLDPAY_IP_ADDRESS))).thenReturn(Optional.of("hello.worldpay.com."));
        when(reverseDnsLookup.lookup(new DnsPointerResourceRecord(UNEXPECTED_IP_ADDRESS))).thenReturn(Optional.of("dns.google."));
    }

    public WorldpayNotificationResourceIT() {
        super("worldpay");
    }

    @Test
    public void shouldHandleAChargeNotification() {
        String transactionId = RandomIdGenerator.newId();
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "CAPTURED")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldHandleARefundNotification() {
        String transactionId = RandomIdGenerator.newId();
        String refundExternalId = String.valueOf(nextLong());
        int refundAmount = 1000;

        String externalChargeId = createNewChargeWithRefund(transactionId, refundExternalId, refundAmount);

        String response = notifyConnector(transactionId, "REFUNDED", refundExternalId)
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));
        assertFrontendChargeStatusIs(externalChargeId, CAPTURED.getValue());
        assertRefundStatus(externalChargeId, refundExternalId, "success", refundAmount);
    }

    @Test
    public void shouldHandleARefundNotification_forAnExpungedCharge() throws Exception {
        String gatewayTransactionId = RandomIdGenerator.newId();
        String refundExternalId = String.valueOf(nextLong());
        String chargeExternalId = randomAlphanumeric(26);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(getTestAccount())
                .withExternalChargeId(chargeExternalId)
                .withTransactionId(gatewayTransactionId);

        ledgerStub.returnLedgerTransactionForProviderAndGatewayTransactionId(testCharge, getPaymentProvider());

        databaseTestHelper.addRefund(refundExternalId, 1000,
                REFUND_SUBMITTED, refundExternalId, ZonedDateTime.now(),
                chargeExternalId);

        String response = notifyConnector(gatewayTransactionId, "REFUNDED", refundExternalId)
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        Map<String, Object> chargeFromDB = databaseTestHelper.getChargeByExternalId(chargeExternalId);
        assertThat(chargeFromDB, is(nullValue()));

        List<Map<String, Object>> refundsByChargeExternalId = databaseTestHelper.getRefundsByChargeExternalId(chargeExternalId);
        assertThat(refundsByChargeExternalId.size(), is(1));
        assertThat(refundsByChargeExternalId.get(0).get("charge_external_id"), is(chargeExternalId));
        assertThat(refundsByChargeExternalId.get(0).get("gateway_transaction_id"), is(refundExternalId));
        assertThat(refundsByChargeExternalId.get(0).get("status"), is("REFUNDED"));
    }

    @Test
    public void shouldReturn500_whenChargeNotInConnectorAndLedgerReturnsAnError() throws Exception {
        String gatewayTransactionId = RandomIdGenerator.newId();
        String refundExternalId = String.valueOf(nextLong());
        String chargeExternalId = randomAlphanumeric(26);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures.withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(getTestAccount())
                .withExternalChargeId(chargeExternalId)
                .withTransactionId(gatewayTransactionId);

        ledgerStub.return500ForFindByProviderAndGatewayTransactionId(getPaymentProvider(), testCharge.getTransactionId());

        notifyConnector(gatewayTransactionId, "REFUNDED", refundExternalId)
                .statusCode(500);
    }

    @Test
    public void shouldIgnoreAuthorisedNotification() {

        String transactionId = RandomIdGenerator.newId();
        String chargeId = createNewChargeWith(CAPTURED, transactionId);

        String response = notifyConnector(transactionId, "AUTHORISED")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURED.getValue());
    }

    @Test
    public void shouldNotAddUnknownStatusToDatabaseFromANotification() {
        String transactionId = RandomIdGenerator.newId();
        String chargeId = createNewChargeWith(CAPTURE_SUBMITTED, transactionId);

        String response = notifyConnector(transactionId, "GARBAGE")
                .statusCode(200)
                .extract().body()
                .asString();

        assertThat(response, is(RESPONSE_EXPECTED_BY_WORLDPAY));

        assertFrontendChargeStatusIs(chargeId, CAPTURE_SUBMITTED.getValue());
    }

    @Test
    public void shouldReturn2xxStatusIfChargeIsNotFoundForTransaction() throws Exception {
        ledgerStub.returnNotFoundForFindByProviderAndGatewayTransactionId("worldpay",
                "unknown-transaction-id");
        notifyConnector("unknown-transaction-id", "GARBAGE")
                .statusCode(200)
                .extract().body()
                .asString();
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIp() {
        given().port(testContext.getPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .header("X-Forwarded-For", UNEXPECTED_IP_ADDRESS)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }
    
    @Test
    public void shouldReturnForbiddenIfXForwardedForHeaderIsMissing() {
        given().port(testContext.getPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    public void shouldFailWhenUnexpectedContentType() {
        given().port(testContext.getPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .contentType(APPLICATION_JSON)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(415);
    }

    private ValidatableResponse notifyConnector(String transactionId, String status) {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status));
    }

    private ValidatableResponse notifyConnector(String transactionId, String status, String reference) {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status, reference));
    }

    private ValidatableResponse notifyConnector(String payload) {
        return given().port(testContext.getPort())
                .body(payload)
                .header("X-Forwarded-For", WORLDPAY_IP_ADDRESS)
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then();
    }

    private String notificationPayloadForTransaction(String transactionId, String status) {
        return TestTemplateResourceLoader.load(WORLDPAY_NOTIFICATION)
                .replace("{{transactionId}}", transactionId)
                .replace("{{status}}", status)
                .replace("{{bookingDateDay}}", "10")
                .replace("{{bookingDateMonth}}", "01")
                .replace("{{bookingDateYear}}", "2017");
    }

    private String notificationPayloadForTransaction(String transactionId, String status, String reference) {
        String payload = notificationPayloadForTransaction(transactionId, status);
        return payload.replace("{{refund-ref}}", reference);
    }

    private String createNewChargeWithRefund(String transactionId, String refundExternalId, long refundAmount) {
        String externalChargeId = createNewChargeWith(CAPTURED, transactionId);
        databaseTestHelper.addRefund(refundExternalId, refundAmount, REFUND_SUBMITTED,
                refundExternalId, ZonedDateTime.now(), externalChargeId);
        return externalChargeId;
    }

    public static class ConnectorAppWithCustomInjector extends ConnectorApp {

        @Override
        protected ConnectorModule getModule(ConnectorConfiguration configuration, Environment environment) {
            return new ConnectorModuleWithOverrides(configuration, environment);
        }
    }

    private static class ConnectorModuleWithOverrides extends ConnectorModule {

        public ConnectorModuleWithOverrides(ConnectorConfiguration configuration, Environment environment) {
            super(configuration, environment);
        }

        @Override
        protected ReverseDnsLookup getReverseDnsLookup() {
            return reverseDnsLookup;
        }
    }
}
