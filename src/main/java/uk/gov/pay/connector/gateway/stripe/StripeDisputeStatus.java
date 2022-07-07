package uk.gov.pay.connector.gateway.stripe;

import java.util.Arrays;

public enum StripeDisputeStatus {
    NEEDS_RESPONSE,
    WON,
    LOST,
    UNDER_REVIEW,
    UNKNOWN;

    public static StripeDisputeStatus byStatus(String status) {
        return Arrays.stream(StripeDisputeStatus.values())
                .filter(c -> c.name().equalsIgnoreCase(status))
                .findFirst()
                .orElse(UNKNOWN);
    }
}
