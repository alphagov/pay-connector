package uk.gov.pay.connector.it.fixtures;

import com.google.common.collect.ImmutableMap;

import java.io.Serializable;

import static uk.gov.pay.connector.util.JsonEncoder.toJson;


public class ChargeApiFixtures {
    private static final String JSON_AMOUNT_KEY = "amount";
    private static final String JSON_REFERENCE_KEY = "reference";
    private static final String JSON_DESCRIPTION_KEY = "description";
    private static final String JSON_GATEWAY_ACC_KEY = "gateway_account_id";
    private static final String JSON_RETURN_URL_KEY = "return_url";
//    private static final String JSON_CHARGE_KEY = "charge_id";
//    private static final String JSON_STATUS_KEY = "status";
//    private static final String JSON_MESSAGE_KEY = "message";

    private static Long defaultAmount = 6234L;
    private static String defaultReference = "a-reference";
    private static String defaultDescription = "a-description";
    private static String defaultReturnUrl = "http://service.url/success-page/";

    public static String aValidCharge(String accountId) {
        return buildCharge(accountId,defaultAmount, defaultReference, defaultDescription, defaultReturnUrl);
    }

    private static String buildCharge(String accountId, Long amount, Serializable reference, String description, String returnUrl) {
        return toJson(ImmutableMap.of(
                JSON_AMOUNT_KEY, amount,
                JSON_REFERENCE_KEY, reference,
                JSON_DESCRIPTION_KEY, description,
                JSON_GATEWAY_ACC_KEY, accountId,
                JSON_RETURN_URL_KEY, returnUrl));
    }

}
