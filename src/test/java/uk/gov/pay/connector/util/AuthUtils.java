package uk.gov.pay.connector.util;

import uk.gov.pay.connector.gateway.model.Auth3dsDetails;

public class AuthUtils {
    
    public static Auth3dsDetails buildAuth3dsDetails() {
        Auth3dsDetails auth3dsDetails = new Auth3dsDetails();
        auth3dsDetails.setPaResponse("sample-pa-response");
        return auth3dsDetails;
    }

}
