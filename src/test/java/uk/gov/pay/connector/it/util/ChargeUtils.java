package uk.gov.pay.connector.it.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.math.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.dao.DatabaseFixtures;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargeUtils {

    public static String createChargePostBody(String accountId) {
        return createChargePostBody("description", 100, accountId, "http://nothing", "default@email.com");
    }
    
    public static String createChargePostBody(String description, long expectedAmount, String accountId, String returnUrl, String email) {
        return toJson(ImmutableMap.builder()
                .put("reference", "Test reference")
                .put("description", description)
                .put("amount", expectedAmount)
                .put("gateway_account_id", accountId)
                .put("return_url", returnUrl)
                .put("email", email)
                .put("delayed_capture", true).build());
    }
    
    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId, String accountId, DatabaseTestHelper databaseTestHelper) {
        long chargeId = RandomUtils.nextInt();
        ExternalChargeId externalChargeId = ExternalChargeId.fromChargeId(chargeId);
        databaseTestHelper.addCharge(chargeId, externalChargeId.toString(), accountId, 6234L, status, "RETURN_URL", gatewayTransactionId);
        return externalChargeId;
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
