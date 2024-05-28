package uk.gov.pay.connector.it.util;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomUtils;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.DatabaseTestHelper;

import java.util.Map;

import static uk.gov.pay.connector.it.resources.ChargesFrontendResourceIT.AGREEMENT_ID;
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

    public static String createChargePostBodyWithAgreement(String description, long expectedAmount, String accountId, String returnUrl, String email) {
        return toJson(Map.of(
                "reference", "Test reference",
                "description", description,
                "amount", expectedAmount,
                "gateway_account_id", accountId,
                "return_url", returnUrl,
                "email", email,
                "save_payment_instrument_to_agreement", true,
                "agreement_id", AGREEMENT_ID,
                "delayed_capture", true));
    }

    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId, String accountId, 
                                                                DatabaseTestHelper databaseTestHelper, String paymentProvider) {
        return createNewChargeWithAccountId(status, gatewayTransactionId, accountId, databaseTestHelper, "email@fake.test", paymentProvider);
    }

    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId, String accountId,
                                                                DatabaseTestHelper databaseTestHelper, String emailAddress, String paymentProvider) {
        return createNewChargeWithAccountId(status, gatewayTransactionId, accountId, databaseTestHelper, emailAddress, paymentProvider, "Test description");
    }

    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId, String accountId,
                                                                DatabaseTestHelper databaseTestHelper, String emailAddress, String paymentProvider, String description) {
        long chargeId = RandomUtils.nextInt();
        ExternalChargeId externalChargeId = ExternalChargeId.fromChargeId(chargeId);
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId.toString())
                .withGatewayAccountId(accountId)
                .withPaymentProvider(paymentProvider)
                .withAmount(6234L)
                .withStatus(status)
                .withTransactionId(gatewayTransactionId)
                .withEmail(emailAddress)
                .withDescription(description)
                .build());
        return externalChargeId;
    }

    public static ExternalChargeId createNewChargeWithAccountId(ChargeStatus status, String gatewayTransactionId,
                                                                String accountId, DatabaseTestHelper databaseTestHelper,
                                                                String paymentProvider, Long gatewayCredentialId) {
        long chargeId = RandomUtils.nextInt();
        ExternalChargeId externalChargeId = ExternalChargeId.fromChargeId(chargeId);
        databaseTestHelper.addCharge(anAddChargeParams()
                .withChargeId(chargeId)
                .withExternalChargeId(externalChargeId.toString())
                .withGatewayAccountId(accountId)
                .withPaymentProvider(paymentProvider)
                .withAmount(6234L)
                .withStatus(status)
                .withTransactionId(gatewayTransactionId)
                .withEmail("email@fake.test")
                .withGatewayCredentialId(gatewayCredentialId)
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
