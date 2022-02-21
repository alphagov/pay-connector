package uk.gov.pay.connector.queue.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TaskType {
    COLLECT_FEE_FOR_STRIPE_FAILED_PAYMENT("collect_fee_for_stripe_failed_payment"),
    HANDLE_STRIPE_WEBHOOK_NOTIFICATION("handle_stripe_webhook_notification"); 

    TaskType(String name) {
        this.name = name;
    }

    private final String name;

    @JsonValue
    public String getName() {
        return name;
    }

}
