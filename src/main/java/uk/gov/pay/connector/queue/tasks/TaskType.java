package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskType {
    COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT("collect_fee_for_stripe_failed_payment"),
    HANDLE_STRIPE_WEBHOOK_NOTIFICATION("handle_stripe_webhook_notification"),
    AUTHORISE_WITH_USER_NOT_PRESENT("authorise_with_user_not_present"),
    DELETE_STORED_PAYMENT_DETAILS("delete_stored_payment_details"),
    RETRY_FAILED_PAYMENT_OR_REFUND_EMAIL("retry_failed_payment_or_refund_email"), 
    SERVICE_ARCHIVED("service_archived");

    TaskType(String name) {
        this.name = name;
    }

    private final String name;

    @JsonValue
    public String getName() {
        return name;
    }

}
