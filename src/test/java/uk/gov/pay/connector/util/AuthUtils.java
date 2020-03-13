package uk.gov.pay.connector.util;

import uk.gov.pay.connector.gateway.model.Auth3dsResult;

public class AuthUtils {
    public static Auth3dsResult buildAuth3dsResult() {
        Auth3dsResult auth3dsResult = new Auth3dsResult();
        auth3dsResult.setPaResponse("sample-pa-response");
        return auth3dsResult;
    }
}
