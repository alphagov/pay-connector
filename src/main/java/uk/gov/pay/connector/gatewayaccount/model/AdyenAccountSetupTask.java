package uk.gov.pay.connector.gatewayaccount.model;

import jakarta.ws.rs.BadRequestException;

public enum AdyenAccountSetupTask {
    BANK_ACCOUNT("bank_account"),
    RESPONSIBLE_PERSON("responsible_person"),
    VAT_NUMBER("vat_number"),
    COMPANY_NUMBER("company_number"),
    DIRECTOR("director"),
    GOVERNMENT_ENTITY_DOCUMENT("government_entity_document"),
    ORGANISATION_DETAILS("organisation_details");

    private final String value;

    AdyenAccountSetupTask(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }

    public static AdyenAccountSetupTask fromPath(String path) {
        try {
            return AdyenAccountSetupTask.valueOf(path.toUpperCase());
        } catch (IllegalArgumentException _) {
            throw new BadRequestException("Task name is not recognised: " + path);
        }
    }
}
