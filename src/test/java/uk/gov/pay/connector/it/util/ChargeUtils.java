package uk.gov.pay.connector.it.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang.math.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import static uk.gov.pay.connector.util.AddChargeParams.AddChargeParamsBuilder.anAddChargeParams;
import static uk.gov.pay.connector.util.JsonEncoder.toJson;

public class ChargeUtils {

    public static String createChargePostBody(String accountId) {
        return createChargePostBody("description", 100, accountId, "http://nothing", "default@email.invalid");
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
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId.toString())
                .withGatewayAccountId(accountId)
                .withAmount(6234L)
                .withStatus(status)
                .withTransactionId(gatewayTransactionId)
                .withEmail("email@fake.test")
                .build());
        return externalChargeId;
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
