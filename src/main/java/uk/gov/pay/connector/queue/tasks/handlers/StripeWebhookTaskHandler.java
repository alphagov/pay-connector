package uk.gov.pay.connector.queue.tasks.handlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StripeWebhookTaskHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookTaskHandler.class);

    public void process(String data) {
        // TODO deserialize and do something with data
        // TODO add webhook event id to MDC
        logger.info("Processing webhook, ignoring");
    }

    public void emitEvent() {
        // TODO emit dispute created event
        // TODO remove webhook event id from MDC
    }
}
