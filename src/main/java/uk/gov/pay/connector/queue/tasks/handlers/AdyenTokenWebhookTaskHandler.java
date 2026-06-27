package uk.gov.pay.connector.queue.tasks.handlers;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.gateway.adyen.webhook.AdyenRecurringTokenWebhookService;

public class AdyenTokenWebhookTaskHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenTokenWebhookTaskHandler.class);

    private final AdyenRecurringTokenWebhookService adyenRecurringTokenWebhookService;

    @Inject
    public AdyenTokenWebhookTaskHandler(AdyenRecurringTokenWebhookService adyenRecurringTokenWebhookService) {
        this.adyenRecurringTokenWebhookService = adyenRecurringTokenWebhookService;
    }

    public void processAdyenTokenWebhookNotification(String payload) {
        LOGGER.info("Processing Adyen token webhook notification");
        adyenRecurringTokenWebhookService.processTokenWebhook(payload);
    }
}
