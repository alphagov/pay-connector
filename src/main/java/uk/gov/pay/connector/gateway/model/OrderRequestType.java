package uk.gov.pay.connector.gateway.model;

public enum OrderRequestType {
    AUTHORISE("authorise"),
    AUTHORISE_3DS("authorise3DS"),
    AUTHORISE_APPLE_PAY("authoriseApplePay"),
    AUTHORISE_GOOGLE_PAY("authoriseGooglePay"),
    CAPTURE("capture"),
    CANCEL("cancel"),
    REFUND("refund"),
    QUERY("query"), 
    DELETE_STORED_PAYMENT_DETAILS("delete_stored_payment_details"),
    STRIPE_TOKEN("authorise.create_token"), 
    STRIPE_CREATE_SOURCE("authorise.create_source"), 
    STRIPE_CREATE_CHARGE("authorise.create_charge"), 
    STRIPE_CREATE_3DS_SOURCE("authorise.create_3ds_source"),
    STRIPE_UPDATE_DISPUTE("dispute.update");

    private final String name;

    OrderRequestType(String s) {
        name = s;
    }

    public String toString() {
        return this.name;
    }
}
