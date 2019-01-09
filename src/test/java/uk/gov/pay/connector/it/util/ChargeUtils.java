package uk.gov.pay.connector.it.util;

import org.apache.commons.lang.math.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.time.ZonedDateTime;
import java.util.Map;

public class ChargeUtils {

    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId, String accountId, DatabaseTestHelper databaseTestHelper) {
        long chargeId = RandomUtils.nextInt();
        ExternalChargeId externalChargeId = ExternalChargeId.fromChargeId(chargeId);
        databaseTestHelper.addCharge(chargeId, externalChargeId.toString(), accountId, 6234L, status, "RETURN_URL", gatewayTransactionId);
        return externalChargeId;
    }

    public static void createNewRefund(RefundStatus status, long chargeId, String refundExternalId, String reference, long amount, DatabaseTestHelper databaseTestHelper) {
        long refundId = RandomUtils.nextInt();
        databaseTestHelper.addRefund(refundId, refundExternalId, reference, amount, status.getValue(), chargeId, ZonedDateTime.now());
    }

    public static DatabaseFixtures.TestCharge createTestCharge(DatabaseTestHelper databaseTestHelper, String paymentProvider, ChargeStatus chargeStatus,
                                                               Map<String,String> credentials, String transactionId) {
        DatabaseFixtures.TestAccount testAccount = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestAccount()
                .withPaymentProvider(paymentProvider)
                .withCredentials(credentials);

        DatabaseFixtures.TestCharge testCharge = DatabaseFixtures
                .withDatabaseTestHelper(databaseTestHelper)
                .aTestCharge()
                .withTestAccount(testAccount)
                .withChargeStatus(chargeStatus)
                .withTransactionId(transactionId);

        testAccount.insert();
        return testCharge.insert();
    }

    public static class ExternalChargeId {
        public final long chargeId;

        public ExternalChargeId(long chargeId) {
            this.chargeId = chargeId;
        }

        @Override
        public String toString() {
            return "charge-" + chargeId;
        }

        public static ExternalChargeId fromChargeId(long chargeId) {
            return new ExternalChargeId(chargeId);
        }
    }
}
