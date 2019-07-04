package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;

import javax.inject.Inject;

public class PaymentStateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStateTransitionEmitterProcess.class);

    private final PaymentStateTransitionQueue paymentStateTransitionQueue;

    @Inject
    public PaymentStateTransitionEmitterProcess(PaymentStateTransitionQueue paymentStateTransitionQueue) {
        this.paymentStateTransitionQueue = paymentStateTransitionQueue;
    }

    public void handleStateTransitionMessages() {
        LOGGER.info("Checking payment state transition queue for new transitions to process");
    }
}
