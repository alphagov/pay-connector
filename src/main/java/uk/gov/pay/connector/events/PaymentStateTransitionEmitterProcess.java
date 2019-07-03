package uk.gov.pay.connector.events.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.queue.PaymentStateTransitionQueue;

import javax.inject.Inject;

public class PaymentStateTransitionEventEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentStateTransitionEventEmitterProcess.class);

    private final PaymentStateTransitionQueue paymentStateTransitionQueue;

    @Inject
    public PaymentStateTransitionEventEmitterProcess(PaymentStateTransitionQueue paymentStateTransitionQueue) {
        this.paymentStateTransitionQueue = paymentStateTransitionQueue;
    }

    public void poll() {
        LOGGER.info("Checking payment state transition queue for new transitions to process");
    }
}
