package uk.gov.pay.connector.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionQueue;
import uk.gov.service.payments.commons.queue.exception.QueueException;

import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class StateTransitionEmitterProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(StateTransitionEmitterProcess.class);

    private final long STATE_TRANSITION_PROCESS_DELAY_IN_MILLISECONDS = 1000;
    private final StateTransitionQueue stateTransitionQueue;
    private final EventFactory eventFactory;
    private EventService eventService;

    @Inject
    public StateTransitionEmitterProcess(
            StateTransitionQueue stateTransitionQueue,
            EventFactory eventFactory,
            StateTransitionQueueMetricEmitter stateTransitionQueueMetricEmitter,
            EventService eventService
    ) {
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventFactory = eventFactory;
        this.eventService = eventService;

        stateTransitionQueueMetricEmitter.register();
    }

    public int getNumberOfNotProcessedMessages() {
        return stateTransitionQueue.size();
    }

    public boolean isReadyForShutdown() {
        return stateTransitionQueue.isEmpty();
    }

    public void handleStateTransitionMessages() throws InterruptedException {
        Optional.ofNullable(stateTransitionQueue.poll(STATE_TRANSITION_PROCESS_DELAY_IN_MILLISECONDS, TimeUnit.MILLISECONDS))
                .ifPresent(this::emitEvents);
    }

    private void emitEvents(StateTransition stateTransition) {
        if (stateTransition.shouldAttempt()) {
            try {
                eventFactory.createEvents(stateTransition)
                        .forEach(event -> {
                            try {
                                eventService.emitAndMarkEventAsEmitted(event);
                            } catch (QueueException e) {
                                handleException(e, stateTransition);
                            }
                        });
                LOGGER.info(
                        "Emitted new state transition event for [eventId={}] [eventType={}]",
                        stateTransition.getIdentifier(),
                        stateTransition.getStateTransitionEventClass().getSimpleName()
                );
            } catch (EventCreationException e) {
                handleException(e, stateTransition);
            }
        } else {
            LOGGER.error(
                    "State transition message failed to process beyond max retries [eventId={}] [eventType={}]:",
                    stateTransition.getIdentifier(),
                    stateTransition.getStateTransitionEventClass().getSimpleName()
            );
        }
    }

    private void handleException(Exception e, StateTransition stateTransition) {
        LOGGER.warn(
                "Failed to emit new event for state transition [eventId={}] [eventType={}] [error={}]",
                stateTransition.getIdentifier(),
                stateTransition.getStateTransitionEventClass().getSimpleName(),
                e.getMessage()
        );
        stateTransitionQueue.offer(stateTransition.getNext());
    }
}
