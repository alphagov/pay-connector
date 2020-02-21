package uk.gov.pay.connector.it.resources.worldpay;

import io.restassured.response.ValidatableResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.pay.connector.app.ConnectorApp;
import uk.gov.pay.connector.it.base.ChargingITestBase;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.junit.DropwizardConfig;
import uk.gov.pay.connector.junit.DropwizardJUnitRunner;
import uk.gov.pay.connector.util.DnsUtils;
import uk.gov.pay.connector.util.RandomIdGenerator;
import uk.gov.pay.connector.util.TestTemplateResourceLoader;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_XML;
import static org.apache.commons.lang.math.RandomUtils.nextLong;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.WORLDPAY_NOTIFICATION;

@RunWith(DropwizardJUnitRunner.class)
@DropwizardConfig(app = ConnectorApp.class, config = "config/test-it-config.yaml")
public class WorldpayNotificationResourceIT extends ChargingITestBase {

    private static final String RESPONSE_EXPECTED_BY_WORLDPAY = "[OK]";
    private static final String NOTIFICATION_PATH = "/v1/api/notifications/worldpay";

    public WorldpayNotificationResourceIT() {
        super("worldpay");
    }

    @Test
    public void shouldHandleAChargeNotification() throws Exception {
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
    public void shouldHandleARefundNotification() throws Exception {
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

        databaseTestHelper.addRefund(refundExternalId, refundExternalId, 1000,
                REFUND_SUBMITTED, randomAlphanumeric(10), ZonedDateTime.now(),
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
        assertThat(refundsByChargeExternalId.get(0).get("reference"), is(refundExternalId));
        assertThat(refundsByChargeExternalId.get(0).get("status"), is("REFUNDED"));
    }

    @Test
    public void shouldIgnoreAuthorisedNotification() throws Exception {

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
    public void shouldNotAddUnknownStatusToDatabaseFromANotification() throws Exception {
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
    public void shouldReturnNon2xxStatusIfChargeIsNotFoundForTransaction() throws Exception {
        ledgerStub.returnNotFoundForFindByProviderAndGatewayTransactionId("worldpay", 
                "unknown-transaction-id");
        notifyConnector("unknown-transaction-id", "GARBAGE")
                .statusCode(403)
                .extract().body()
                .asString();
    }

    @Test
    public void shouldReturnForbiddenIfRequestComesFromUnexpectedIp() {
        given().port(testContext.getPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .header("X-Forwarded-For", "8.8.8.8, 123.1.23.32")
                .contentType(TEXT_XML)
                .post(NOTIFICATION_PATH)
                .then()
                .statusCode(403);
    }

    @Test
    public void shouldReturnForbiddenIfXForwardedForHeaderIsMalformed() {
        given().port(testContext.getPort())
                .body(notificationPayloadForTransaction("any", "WHATEVER"))
                .header("X-Forwarded-For", "something is wrong, 8.8.8.8")
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

    private ValidatableResponse notifyConnector(String transactionId, String status) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status));
    }

    private ValidatableResponse notifyConnector(String transactionId, String status, String reference) throws Exception {
        return notifyConnector(notificationPayloadForTransaction(transactionId, status, reference));
    }

    private ValidatableResponse notifyConnector(String payload) {
        String validIp = new DnsUtils().dnsLookup("build.ci.pymnt.uk").get();
        String xForwardedForHeader = format("%s, %s", validIp, "8.8.8.8");
        return given().port(testContext.getPort())
                .body(payload)
                .header("X-Forwarded-For", xForwardedForHeader)
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
        String chargeId = externalChargeId.substring(externalChargeId.indexOf("-") + 1);
        databaseTestHelper.addRefund(refundExternalId, refundExternalId, refundAmount, REFUND_SUBMITTED,
                randomAlphanumeric(10), ZonedDateTime.now(),
                externalChargeId);
        return externalChargeId;
    }

}
