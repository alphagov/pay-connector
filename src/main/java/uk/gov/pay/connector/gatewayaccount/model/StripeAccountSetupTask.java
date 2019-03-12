package uk.gov.pay.connector.gatewayaccount.model;

public enum StripeAccountSetupTask {

    BANK_ACCOUNT,
    RESPONSIBLE_PERSON,
    @Deprecated
    ORGANISATION_DETAILS,
    VAT_NUMBER_COMPANY_NUMBER

}
