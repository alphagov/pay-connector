package uk.gov.pay.connector.queue.tasks.handlers.adyen;

import io.github.netmikey.logunit.api.LogCapturer;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import uk.gov.pay.connector.extension.AppWithPostgresAndSqsExtension;
import uk.gov.pay.connector.fee.dao.FeeDao;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenTransferNotificationHandlerForFees;
import uk.gov.pay.connector.util.RandomIdGenerator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.pay.connector.util.RandomTestDataGeneratorUtils.secureRandomLong;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.ADYEN_TRANSFER_NOTIFICATION_FOR_FEES;
import static uk.gov.pay.connector.util.TestTemplateResourceLoader.load;

class AdyenTransferNotificationHandlerIT {

    @RegisterExtension
    public static AppWithPostgresAndSqsExtension app = new AppWithPostgresAndSqsExtension();

    @RegisterExtension
    LogCapturer logs = LogCapturer.create().captureForType(AdyenTransferNotificationHandlerForFees.class);

    private DatabaseFixtures.TestAccount defaultTestAccount;
    private DatabaseFixtures.TestCharge testCharge;
    FeeDao feeDao;

    private AdyenTransferNotificationHandler adyenTransferNotificationHandler;

    @BeforeEach
    void setUp() {
        this.defaultTestAccount = app.getDatabaseFixtures()
                .aTestAccount()
                .insert();

        testCharge = app.getDatabaseFixtures()
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(secureRandomLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .insert();
        adyenTransferNotificationHandler = app.getInstanceFromGuiceContainer(AdyenTransferNotificationHandler.class);
        feeDao = app.getInstanceFromGuiceContainer(FeeDao.class);
    }

    @Test
    void shouldProcessFeeNotificationsForFixedCommission() {
        DatabaseFixtures.TestCharge charge = app.getDatabaseFixtures()
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(secureRandomLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .insert();

        String payload = createPaymentFeeTransferNotification(charge, "Commission", "Fixed");

        adyenTransferNotificationHandler.process(payload);

        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(charge.getChargeId());

        assertThat(feesForCharge.size(), is(3));

        Map<String, Object> gatewayFeeRecord = getFeeRecord(feesForCharge, "gateway");
        assertFeeDetails(gatewayFeeRecord, "gateway", 10, is(nullValue()));

        Map<String, Object> fraudProtectionRecord = getFeeRecord(feesForCharge, "fraud_protection");
        assertFeeDetails(fraudProtectionRecord, "fraud_protection", 20, is(nullValue()));

        Map<String, Object> transactionFeeRecord = getFeeRecord(feesForCharge, "transaction");
        assertFeeDetails(transactionFeeRecord, "transaction", 43, is("fixed"));
    }

    @ParameterizedTest
    @CsvSource({
            "Commission, Variable fee, transaction, variable",
            "Interchange, Some desc, transaction,interchange",
            "SchemeFee, Some desc, transaction,scheme_fee"
    })
    void shouldProcessNonFixedFeeNotifications(String platformPaymentType, String description,
                                               String expectedFeeType,
                                               String expectedFeeSubType) {
        String payload = createPaymentFeeTransferNotification(testCharge, platformPaymentType, description);

        adyenTransferNotificationHandler.process(payload);

        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(testCharge.getChargeId());

        assertThat(feesForCharge.size(), is(1));

        Map<String, Object> gatewayFeeRecord = getFeeRecord(feesForCharge, expectedFeeType);
        assertFeeDetails(gatewayFeeRecord, expectedFeeType, 73, is(expectedFeeSubType));
    }

    @Test
    void shouldSaveFeesWhenProcessingMultipleTransferNotificationsConcurrently() throws InterruptedException {
        DatabaseFixtures.TestCharge charge = app.getDatabaseFixtures()
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(secureRandomLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .insert();

        String fixedFeeTransferNotification = createPaymentFeeTransferNotification(charge, "Commission", "Fixed");
        String variableFeeTransferNotification = createPaymentFeeTransferNotification(charge, "Commission", "Variable");

        List<Callable<Void>> authoriseTasks = List.of(
                () -> {
                    adyenTransferNotificationHandler.process(fixedFeeTransferNotification);
                    return null;
                },
                () -> {
                    adyenTransferNotificationHandler.process(variableFeeTransferNotification);
                    return null;
                }
        );

        invokeAll(authoriseTasks);

        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(charge.getChargeId());

        assertThat(feesForCharge.size(), is(4));

        Map<String, Object> feeRecord = getFeeRecord(feesForCharge, "gateway");
        assertFeeDetails(feeRecord, "gateway", 10, is(nullValue()));

        feeRecord = getFeeRecord(feesForCharge, "fraud_protection");
        assertFeeDetails(feeRecord, "fraud_protection", 20, is(nullValue()));

        feeRecord = getFeeRecord(feesForCharge, "transaction", "fixed");
        assertFeeDetails(feeRecord, "transaction", 43, is("fixed"));

        feeRecord = getFeeRecord(feesForCharge, "transaction", "variable");
        assertFeeDetails(feeRecord, "transaction", 73, is("variable"));
    }

    @Test
    void shouldProcessRefundFeeNotificationsForPaymentFee() {
        var refundExternalId = RandomIdGenerator.newId();
        DatabaseFixtures.TestCharge testCharge = app.getDatabaseFixtures()
                .aTestCharge()
                .withTestAccount(defaultTestAccount)
                .withChargeId(secureRandomLong())
                .withExternalChargeId(RandomIdGenerator.newId())
                .insert();

        var refund = app.getDatabaseFixtures()
                .aTestRefund()
                .withTestCharge(testCharge)
                .withExternalRefundId(refundExternalId)
                .insert();

        var feeAmount = 10L;
        String payload = createRefundFeeTransferNotification(testCharge, refundExternalId);
        payload = payload.replace("73", Long.toString(feeAmount));

        adyenTransferNotificationHandler.process(payload);

        List<Map<String, Object>> feesForRefund = app.getDatabaseTestHelper().getFeesByRefundId(refund.getId());

        assertThat(feesForRefund.size(), is(1));

        Map<String, Object> gatewayFeeRecord = getFeeRecord(feesForRefund, "gateway");
        assertFeeDetails(gatewayFeeRecord, "gateway", feeAmount, is(nullValue()));
    }

    @ParameterizedTest
    @CsvSource({
            "capture, received",
            "internalTransfer, booked"
    })
    void shouldIgnoreFeeNotificationForUnknownStatusAndType(String type, String status) {
        String payload = getFeeTransactionNotificationPayload(testCharge.getExternalChargeId(), "",
                "Commission", status, type, "some desc");

        adyenTransferNotificationHandler.process(payload);

        List<Map<String, Object>> feesForCharge = app.getDatabaseTestHelper().getFeesByChargeId(testCharge.getChargeId());

        assertThat(feesForCharge.size(), is(0));
        logs.assertContains("Ignored transfer notification for unknown type or status");
    }

    private String createPaymentFeeTransferNotification(DatabaseFixtures.TestCharge charge,
                                                        String platformPaymentType,
                                                        String description) {
        return getFeeTransactionNotificationPayload(charge.getExternalChargeId(), "",
                platformPaymentType, "captured", "capture", description);
    }

    private String createRefundFeeTransferNotification(DatabaseFixtures.TestCharge charge, String refundExternalId) {
        return getFeeTransactionNotificationPayload(charge.getExternalChargeId(), refundExternalId,
                "PaymentFee", "refunded", "refund", "Fixed");
    }

    private static void assertFeeDetails(Map<String, Object> gatewayFeeRecord, String feeType,
                                         long feeAmount, Matcher<Object> matcher) {
        assertThat(gatewayFeeRecord.get("fee_type"), is(feeType));
        assertThat(gatewayFeeRecord.get("fee_sub_type"), matcher);
        assertThat(gatewayFeeRecord.get("amount_collected"), is(feeAmount));
    }

    private static Map<String, Object> getFeeRecord(List<Map<String, Object>> feesForCharge, String feeType) {
        return feesForCharge.stream()
                .filter(row -> row.get("fee_type").equals(feeType))
                .findFirst().get();
    }

    private static Map<String, Object> getFeeRecord(List<Map<String, Object>> feesForCharge, String feeType, String feeSubType) {
        return feesForCharge.stream()
                .filter(row -> row.get("fee_type").equals(feeType))
                .filter(row -> row.get("fee_sub_type").equals(feeSubType))
                .findFirst().get();
    }

    private String getFeeTransactionNotificationPayload(String chargeExternalId,
                                                        String refundExternalId,
                                                        String platformPaymentType,
                                                        String status,
                                                        String type,
                                                        String description) {
        return load(ADYEN_TRANSFER_NOTIFICATION_FOR_FEES)
                .replace("{{paymentMerchantReference}}", chargeExternalId)
                .replace("{{platformPaymentType}}", platformPaymentType)
                .replace("{{modificationMerchantReference}}", refundExternalId)
                .replace("{{status}}", status)
                .replace("{{type}}", type)
                .replace("{{description}}", description);
    }

    private void invokeAll(List<Callable<Void>> tasks) throws InterruptedException {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            executor.invokeAll(tasks);
        }
    }
}
