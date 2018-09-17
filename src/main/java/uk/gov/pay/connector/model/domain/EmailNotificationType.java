package uk.gov.pay.connector.model.domain;

public enum EmailNotificationType {
    REFUND_ISSUED, PAYMENT_CONFIRMED;

    public static EmailNotificationType fromString(String type) {
        try {
            return EmailNotificationType.valueOf(type.toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("EmailNotificationType status not recognized: " + type);
        }
    }

}
