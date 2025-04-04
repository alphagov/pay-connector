package uk.gov.pay.connector.queue.statetransition;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.persist.Transactional;
import io.dropwizard.core.setup.Environment;
import io.prometheus.client.Counter;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;
import uk.gov.pay.connector.refund.service.RefundStateEventMap;

import jakarta.inject.Inject;
import java.time.Instant;
import java.time.ZonedDateTime;

import static java.lang.String.format;
import static net.logstash.logback.argument.StructuredArguments.kv;

public class StateTransitionService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private StateTransitionQueue stateTransitionQueue;
    private EventService eventService;
    private MetricRegistry metricRegistry;

    private static final Counter stateTransitionCounter = Counter.build()
            .name("state_transition_total")
            .help("Count of state transitions")
            .labelNames("gatewayName", "gatewayAccountType", "toState")
            .register();

    @Inject
    public StateTransitionService(StateTransitionQueue stateTransitionQueue,
                                  EventService eventService,
                                  Environment environment) {
        this.stateTransitionQueue = stateTransitionQueue;
        this.eventService = eventService;
        this.metricRegistry = environment.metrics();
    }

    @Transactional
    public void offerRefundStateTransition(RefundEntity refundEntity, RefundStatus refundStatus) {
        Class refundEventClass = RefundStateEventMap.calculateRefundEventClass(refundEntity.getUserExternalId(), refundStatus);
        RefundStateTransition refundStateTransition = new RefundStateTransition(refundEntity.getExternalId(), refundStatus, refundEventClass);
        stateTransitionQueue.offer(refundStateTransition);

        eventService.recordOfferedEvent(ResourceType.REFUND,
                refundEntity.getExternalId(),
                Event.eventTypeForClass(refundEventClass),
                Instant.now());
    }

    @Transactional
    public void offerPaymentStateTransition(String externalId, ChargeStatus fromChargeState, ChargeStatus targetChargeState, ChargeEventEntity chargeEventEntity) {
        PaymentGatewayStateTransitions.getInstance()
                .getEventForTransition(fromChargeState, targetChargeState)
                .ifPresent(eventClass -> {
                    offerPaymentStateTransition(externalId, fromChargeState, targetChargeState, chargeEventEntity, eventClass);
                });
    }

    @Transactional
    public <T extends Event> void offerPaymentStateTransition(
            String externalId, ChargeStatus fromChargeState, ChargeStatus targetChargeState,
            ChargeEventEntity chargeEventEntity, Class<T> eventClass) {

        PaymentStateTransition transition = new PaymentStateTransition(chargeEventEntity.getId(), eventClass);
        stateTransitionQueue.offer(transition);

        var logMessage = format("Offered payment state transition to emitter queue [from=%s] [to=%s] [chargeEventId=%s] [chargeId=%s]",
                fromChargeState, targetChargeState, chargeEventEntity.getId(), externalId);

        incrementPrometheusStateTransitionCounter(targetChargeState, chargeEventEntity);
        incrementPerGatewayStateTransitionCounter(targetChargeState, chargeEventEntity);
        incrementPerGatewayStateTransitionMeter(targetChargeState, chargeEventEntity);
        incrementPerGatewayAccountStateTransitionCounter(targetChargeState, chargeEventEntity);

        Object[] structuredArgs = ArrayUtils.addAll(
                chargeEventEntity.getChargeEntity().getStructuredLoggingArgs(),
                kv("from_state", fromChargeState),
                kv("to_state", targetChargeState));

        logger.info(logMessage, structuredArgs);

        eventService.recordOfferedEvent(ResourceType.PAYMENT,
                externalId,
                Event.eventTypeForClass(eventClass),
                chargeEventEntity.getUpdated().toInstant());
    }

    private void incrementPrometheusStateTransitionCounter(ChargeStatus targetChargeState, ChargeEventEntity chargeEventEntity) {
        stateTransitionCounter.labels(
                chargeEventEntity.getChargeEntity().getPaymentProvider().toLowerCase(),
                chargeEventEntity.getChargeEntity().getGatewayAccount().getType().toLowerCase(),
                targetChargeState.toString().toLowerCase()).inc();
    }

    private void incrementPerGatewayAccountStateTransitionCounter(ChargeStatus targetChargeState, ChargeEventEntity chargeEventEntity) {
        metricRegistry.counter(String.format(
                "state-transition.%s.%s.to.%s",
                chargeEventEntity.getChargeEntity().getGatewayAccount().getType(),
                chargeEventEntity.getChargeEntity().getPaymentProvider(),
                targetChargeState)).inc();
    }

    private void incrementPerGatewayStateTransitionMeter(ChargeStatus targetChargeState, ChargeEventEntity chargeEventEntity) {
        metricRegistry.meter(String.format(
                "state-transition.%s.%s.to.%s.rate",
                chargeEventEntity.getChargeEntity().getGatewayAccount().getType(),
                chargeEventEntity.getChargeEntity().getPaymentProvider(),
                targetChargeState)).mark();
    }

    private void incrementPerGatewayStateTransitionCounter(ChargeStatus targetChargeState, ChargeEventEntity chargeEventEntity) {
        metricRegistry.counter(String.format(
                "state-transition.%s.%s.to.%s",
                chargeEventEntity.getChargeEntity().getGatewayAccount().getType(),
                chargeEventEntity.getChargeEntity().getPaymentProvider(),
                targetChargeState)).inc();
    }

    @Transactional
    public void offerStateTransition(StateTransition stateTransition, Event event,
                                     ZonedDateTime doNotRetryEmitUntilDate) {
        stateTransitionQueue.offer(stateTransition);
        eventService.recordOfferedEvent(event.getResourceType(), event.getResourceExternalId(),
                event.getEventType(), event.getTimestamp(), doNotRetryEmitUntilDate);
    }
}
