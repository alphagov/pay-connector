package uk.gov.pay.connector.gateway.adyen.webhook.model;

import static uk.gov.pay.connector.gateway.adyen.webhook.model.AdyenEnvironment.LIVE;

public record AdyenWebhookNotification(
        AdyenWebhookEvent event,
        AdyenEnvironment environment,
        boolean usesAdyenNotificationItem
) {
    
    public boolean isLive() {
        return LIVE.equals(environment);
    }
}
