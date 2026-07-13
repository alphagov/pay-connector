package uk.gov.pay.connector.gatewayaccount.model;

import jakarta.ws.rs.BadRequestException;

public enum AdyenAccountSetupStatus {
    NOT_STARTED,
    COMPLETED;
    
    public static AdyenAccountSetupStatus fromPath(String path) {
        try {
            return AdyenAccountSetupStatus.valueOf(path.toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new BadRequestException("Status is not recognised: " + path);
        }
    }

}
