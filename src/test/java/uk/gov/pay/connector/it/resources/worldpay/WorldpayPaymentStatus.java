package uk.gov.pay.connector.it.resources.worldpay;

public enum WorldpayPaymentStatus {
    AUTHORISED("AUTHORISED"),REFUSED("REFUSED"),CAPTURED("CAPTURED");

    private String status;

    WorldpayPaymentStatus(String status) {
        this.status = status;
    }

    public String value() {
        return status;
    }
}
