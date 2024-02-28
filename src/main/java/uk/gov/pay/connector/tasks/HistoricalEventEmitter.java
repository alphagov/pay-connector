package uk.gov.pay.connector.tasks;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.exception.ChargeNotFoundRuntimeException;
import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.events.EventService;
import uk.gov.pay.connector.events.dao.EmittedEventDao;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.EventFactory;
import uk.gov.pay.connector.events.model.charge.BackfillerGatewayTransactionIdSet;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.FeeIncurredEvent;
import uk.gov.pay.connector.events.model.charge.Gateway3dsInfoObtained;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsSubmittedByAPI;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsTakenFromPaymentInstrument;
import uk.gov.pay.connector.events.model.refund.RefundEvent;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.RefundStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransitionService;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.service.RefundStateEventMap;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.ZonedDateTime.now;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;

public class HistoricalEventEmitter {
    public static final List<ChargeStatus> TERMINAL_AUTHENTICATION_STATES = List.of(
            AUTHORISATION_3DS_REQUIRED,
            AUTHORISATION_SUBMITTED,
            AUTHORISATION_SUCCESS,
            AUTHORISATION_ABORTED,
            AUTHORISATION_REJECTED,
            AUTHORISATION_ERROR,
            AUTHORISATION_UNEXPECTED_ERROR,
            AUTHORISATION_TIMEOUT,
            AUTHORISATION_CANCELLED
    );
    private static final Logger logger = LoggerFactory.getLogger(HistoricalEventEmitter.class);
    private static final List<ChargeStatus> INTERMEDIATE_READY_STATES = List.of(AUTHORISATION_3DS_READY,
            AUTHORISATION_READY,
            CAPTURE_READY,
            EXPIRE_CANCEL_READY,
            SYSTEM_CANCEL_READY,
            USER_CANCEL_READY);
    private final EmittedEventDao emittedEventDao;
    private final RefundDao refundDao;
    private final ChargeService chargeService;
    private PaymentGatewayStateTransitions paymentGatewayStateTransitions;
    private EventService eventService;
    private StateTransitionService stateTransitionService;
    private Long doNotRetryEmitUntilDuration;

    @Inject
    public HistoricalEventEmitter(EmittedEventDao emittedEventDao,
                                  RefundDao refundDao,
                                  EventService eventService,
                                  StateTransitionService stateTransitionService,
                                  ChargeService chargeService) {
        this(emittedEventDao, refundDao, chargeService, eventService, stateTransitionService, null);
    }

    public HistoricalEventEmitter(EmittedEventDao emittedEventDao,
                                  RefundDao refundDao, ChargeService chargeService,
                                  EventService eventService, StateTransitionService stateTransitionService,
                                  Long doNotRetryEmitUntilDuration) {
        this.emittedEventDao = emittedEventDao;
        this.refundDao = refundDao;
        this.chargeService = chargeService;
        this.paymentGatewayStateTransitions = PaymentGatewayStateTransitions.getInstance();
        this.eventService = eventService;
        this.stateTransitionService = stateTransitionService;
        this.doNotRetryEmitUntilDuration = doNotRetryEmitUntilDuration;
    }

    public void processPaymentEvents(ChargeEntity charge, boolean forceEmission) {
        List<ChargeEventEntity> chargeEventEntities = getSortedChargeEvents(charge);

        processChargeStateTransitionEvents(charge.getId(), chargeEventEntities, forceEmission);
        processFeeIncurredEvent(charge, forceEmission);
        processPaymentDetailEnteredEvent(chargeEventEntities, forceEmission);
        processUserEmailCollectedEvent(charge, chargeEventEntities, forceEmission);
        processGatewayTransactionIdSetEvent(charge, chargeEventEntities, forceEmission);
        process3DSVersionEvent(charge,chargeEventEntities, forceEmission);
    }

    private void process3DSVersionEvent(ChargeEntity charge, List<ChargeEventEntity> chargeEventEntities, boolean forceEmission) {
        if (charge.getChargeCardDetails().get3dsRequiredDetails() != null && isNotBlank(charge.getChargeCardDetails().get3dsRequiredDetails().getThreeDsVersion())) {
                var chargeEvent = chargeEventEntities
                        .stream()
                        .filter(event -> TERMINAL_AUTHENTICATION_STATES.contains(event.getStatus()))
                        .findFirst();

                chargeEvent.ifPresent(event -> {
                    var threeDsInfoEvent = Gateway3dsInfoObtained.from(charge, event.getUpdated().toInstant());
                    boolean hasBeenEmittedBefore = emittedEventDao.hasBeenEmittedBefore(threeDsInfoEvent);
                    if (forceEmission || !hasBeenEmittedBefore) {
                        eventService.emitAndRecordEvent(threeDsInfoEvent, getDoNotRetryEmitUntilDate());
                        logger.info("Gateway 3DS Info Obtained event re-emitted for [chargeExternalId={}]", charge.getExternalId());
                    }
                });
        }
    }

    private void processFeeIncurredEvent(ChargeEntity charge, boolean forceEmission) {
        // We only want to emit the FEE_INCURRED event for charges using the new Stripe pricing. The original Stripe
        // pricing has no FeeType
        if (!filterFeesForStripeV2(charge.getFees()).isEmpty()) {
            try {
                var feeIncurredEvent = FeeIncurredEvent.from(charge);
                boolean hasBeenEmittedBefore = emittedEventDao.hasBeenEmittedBefore(feeIncurredEvent);
                if (forceEmission || !hasBeenEmittedBefore) {
                    eventService.emitAndRecordEvent(feeIncurredEvent, getDoNotRetryEmitUntilDate());
                } else {
                    logger.info("Charge history event emitted before [chargeExternalId={}]", charge.getExternalId());
                }
            } catch (EventCreationException e) {
                logger.warn(format("Failed to create fee incurred event [%s], exception: [%s]", charge.getExternalId(), e.getMessage()));
            }
        }
    }

    private List<FeeEntity> filterFeesForStripeV2(List<FeeEntity> feesList) {
        return feesList.stream()
                .filter(feeEntity -> feeEntity.getFeeType() != null)
                .collect(Collectors.toList());
    }

    public void processRefundEvents(String chargeExternalId, boolean forceEmission) {
        List<RefundHistory> refundHistories = refundDao.searchAllHistoryByChargeExternalId(chargeExternalId);
        refundHistories
                .stream()
                .sorted(Comparator.comparing(RefundHistory::getHistoryStartDate))
                .forEach(refundHistory -> emitAndPersistEventForRefundHistoryEntry(refundHistory, forceEmission));
    }

    public void emitEventsForRefund(String refundExternalId, boolean shouldForceEmission) {
        List<RefundHistory> refundHistories = refundDao.getRefundHistoryByRefundExternalId(refundExternalId);
        refundHistories
                .stream()
                .sorted(Comparator.comparing(RefundHistory::getHistoryStartDate))
                .forEach(refundHistory -> emitAndPersistEventForRefundHistoryEntry(refundHistory, shouldForceEmission));
    }

    @Transactional
    public void emitAndPersistEventForRefundHistoryEntry(RefundHistory refundHistory, boolean shouldForceEmission) {
        Class<? extends RefundEvent> refundEventClass = RefundStateEventMap.calculateRefundEventClass(
                refundHistory.getUserExternalId(), refundHistory.getStatus());
        Charge charge = chargeService.findCharge(refundHistory.getChargeExternalId())
                .orElseThrow(() -> new ChargeNotFoundRuntimeException(refundHistory.getChargeExternalId()));
        Event event = EventFactory.createRefundEvent(refundHistory, refundEventClass,
                charge);

        if (shouldForceEmission) {
            emitRefundEvent(refundHistory, refundEventClass, event);
        } else {
            boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

            if (emittedBefore) {
                logger.info("Refund history event emitted before [refundExternalId={}] [refundHistoryId={}]", refundHistory.getExternalId(), refundHistory.getId());
            } else {
                emitRefundEvent(refundHistory, refundEventClass, event);
            }
        }
    }

    private void emitRefundEvent(RefundHistory refundHistory, Class<? extends RefundEvent> refundEventClass, Event event) {
        RefundStateTransition stateTransition = new RefundStateTransition(
                refundHistory.getExternalId(),
                refundHistory.getStatus(),
                refundEventClass);

        logger.info("Processing new refund history event: [refundExternalId={}] [refundHistoryId={}]", refundHistory.getExternalId(), refundHistory.getId());

        stateTransitionService.offerStateTransition(stateTransition, event, getDoNotRetryEmitUntilDate());
    }

    private List<ChargeEventEntity> getSortedChargeEvents(ChargeEntity charge) {
        return charge.getEvents()
                .stream()
                .sorted(Comparator.comparing(ChargeEventEntity::getUpdated))
                .sorted(HistoricalEventEmitter::sortOutOfOrderCaptureEvents)
                .collect(Collectors.toList());
    }

    private static int sortOutOfOrderCaptureEvents(ChargeEventEntity lhs, ChargeEventEntity rhs) {
        // puts CAPTURE_SUBMITTED at top of the events list (after first pass of sorting)
        // when timestamp for CAPTURED is same or before CAPTURE_SUBMITTED timestamp
        if (lhs.getStatus().equals(ChargeStatus.CAPTURE_SUBMITTED)
                && rhs.getStatus().equals(ChargeStatus.CAPTURED)) {
            return -1;
        } else {
            return 0;
        }
    }

    private void processChargeStateTransitionEvents(long currentId, List<ChargeEventEntity> chargeEventEntities,
                                                    boolean forceEmission) {
        for (int index = 0; index < chargeEventEntities.size(); index++) {
            ChargeStatus fromChargeState;
            ChargeEventEntity chargeEventEntity = chargeEventEntities.get(index);

            if (index == 0) {
                fromChargeState = ChargeStatus.UNDEFINED;
            } else {
                fromChargeState = chargeEventEntities.get(index - 1).getStatus();
            }

            processSingleChargeStateTransitionEvent(currentId, fromChargeState, chargeEventEntity,
                    forceEmission);
        }
    }

    private void processSingleChargeStateTransitionEvent(long currentId, ChargeStatus fromChargeState,
                                                         ChargeEventEntity chargeEventEntity,
                                                         boolean forceEmission) {
        Optional<Class<Event>> eventForTransition = getEventForTransition(fromChargeState, chargeEventEntity);

        eventForTransition.ifPresent(eventType -> {
            PaymentStateTransition transition = new PaymentStateTransition(chargeEventEntity.getId(), eventType);
            offerPaymentStateTransitionEvents(currentId, chargeEventEntity, transition, forceEmission);
        });
    }

    private Optional<Class<Event>> getEventForTransition(ChargeStatus fromChargeStatus,
                                                         ChargeEventEntity chargeEventEntity) {
        Optional<Class<Event>> eventForTransition = paymentGatewayStateTransitions
                .getEventForTransition(fromChargeStatus, chargeEventEntity.getStatus());

        if (eventForTransition.isEmpty()) {
            Optional<ChargeStatus> intermediateChargeStatus = paymentGatewayStateTransitions
                    .getIntermediateChargeStatus(fromChargeStatus, chargeEventEntity.getStatus());

            if (intermediateChargeStatus.isPresent() && INTERMEDIATE_READY_STATES.contains(intermediateChargeStatus.get())) {
                eventForTransition = paymentGatewayStateTransitions
                        .getEventForTransition(intermediateChargeStatus.get(), chargeEventEntity.getStatus());
            } else {
                logger.info("Historical Event emitter for Charge [{}] and Event [{}] - Couldn't derive event for transition from [{}] to [{}]",
                        chargeEventEntity.getChargeEntity().getId(),
                        chargeEventEntity.getId(),
                        fromChargeStatus,
                        chargeEventEntity.getStatus()
                );
            }
        }
        return eventForTransition;
    }

    private void offerPaymentStateTransitionEvents(long currentId, ChargeEventEntity chargeEventEntity,
                                                   PaymentStateTransition transition, boolean forceEmission) {
        Event event = EventFactory.createPaymentEvent(chargeEventEntity, transition.getStateTransitionEventClass());

        if (forceEmission) {
            offerStateTransitionEvent(currentId, chargeEventEntity, transition, event);
        } else {
            final boolean emittedBefore = emittedEventDao.hasBeenEmittedBefore(event);

            if (emittedBefore) {
                logger.info("[{}] - found - charge event [{}] emitted before", currentId, chargeEventEntity.getId());
            } else {
                offerStateTransitionEvent(currentId, chargeEventEntity, transition, event);
            }
        }
    }

    private void offerStateTransitionEvent(long currentId, ChargeEventEntity chargeEventEntity, PaymentStateTransition transition, Event event) {
        logger.info("[{}] - found - emitting {} for charge event [{}] ", currentId, event, chargeEventEntity.getId());
        stateTransitionService.offerStateTransition(transition, event, getDoNotRetryEmitUntilDate());
    }

    private void processPaymentDetailEnteredEvent(List<ChargeEventEntity> chargeEventEntities, boolean forceEmission) {
        // transition to AUTHORISATION_READY does not record state transition, verify details have been entered by
        // checking against any terminal authentication transition
        chargeEventEntities
                .stream()
                .filter(event -> !isATelephonePaymentNotification(chargeEventEntities))
                .filter(event -> isValidPaymentDetailsEnteredTransition(chargeEventEntities, event))
                .map(this::determinePaymentDetailsEnteredEvent)
                .filter(event -> forceEmission || !emittedEventDao.hasBeenEmittedBefore(event))
                .forEach(event -> eventService.emitAndRecordEvent(event, getDoNotRetryEmitUntilDate()));
    }

    private Event determinePaymentDetailsEnteredEvent(ChargeEventEntity chargeEventEntity) {
        switch (chargeEventEntity.getChargeEntity().getAuthorisationMode()) {
            case MOTO_API:
                return PaymentDetailsSubmittedByAPI.from(chargeEventEntity);
            case AGREEMENT:
                return PaymentDetailsTakenFromPaymentInstrument.from(chargeEventEntity);
            default:
                return PaymentDetailsEntered.from(chargeEventEntity);
        }
    }

    private void processUserEmailCollectedEvent(ChargeEntity charge, List<ChargeEventEntity> chargeEventEntities, boolean forceEmission) {
        // email patched by frontend before submitting card details is not included in state transition.
        // emits event, when charge has email and has entering card details state.
        // Could generate events even if service prefills email address, but there is no way to distinguish 
        // if service prefilled email or is submitted by user
        if (isNotBlank(charge.getEmail())) {
            boolean hasEnteringCardDetailsEvent = chargeEventEntities
                    .stream().anyMatch(event -> ENTERING_CARD_DETAILS.equals(event.getStatus()));

            if (hasEnteringCardDetailsEvent) {
                BackfillerRecreatedUserEmailCollected userEmailCollectedEvent = BackfillerRecreatedUserEmailCollected.from(charge);
                if (forceEmission || !emittedEventDao.hasBeenEmittedBefore(userEmailCollectedEvent)) {
                    eventService.emitAndRecordEvent(userEmailCollectedEvent, getDoNotRetryEmitUntilDate());
                }
            }
        }
    }

    /**
     * GatewayTransactionId is set before a charge is authorised (for Worldpay payments). gateway_transaction_id is included in PAYMENT_ENTERED_DETAILS event.
     * But sometimes, the thread could terminate without updating charge status and the charge will eventually expire.
     * In this case gateway_transaction_id is not emitted to Ledger. Backfill gateway_trasaction_id so the charge can be expunged.
     */
    private void processGatewayTransactionIdSetEvent(ChargeEntity charge, List<ChargeEventEntity> chargeEventEntities, boolean forceEmission) {
        if (isBlank(charge.getGatewayTransactionId())) {
            return;
        }

        boolean hasEnteringCardDetailsEvent = chargeEventEntities
                .stream().anyMatch(event -> ENTERING_CARD_DETAILS.equals(event.getStatus()));

        if (!hasEnteringCardDetailsEvent || isATelephonePaymentNotification(chargeEventEntities)) {
            return;
        }

        boolean hasAnyValidAuthorisationState = chargeEventEntities
                .stream()
                .anyMatch(event -> isValidPaymentDetailsEnteredTransition(chargeEventEntities, event));

        if (!hasAnyValidAuthorisationState) {
            BackfillerGatewayTransactionIdSet backfillerGatewayTransactionIdSet = BackfillerGatewayTransactionIdSet.from(charge);
            if (forceEmission || !emittedEventDao.hasBeenEmittedBefore(backfillerGatewayTransactionIdSet)) {
                eventService.emitAndRecordEvent(backfillerGatewayTransactionIdSet, getDoNotRetryEmitUntilDate());
            }
        }
    }

    private boolean isATelephonePaymentNotification(List<ChargeEventEntity> chargeEventEntities) {
        return chargeEventEntities.stream()
                .map(ChargeEventEntity::getStatus)
                .collect(Collectors.toList()).contains(PAYMENT_NOTIFICATION_CREATED);
    }

    private boolean isValidPaymentDetailsEnteredTransition(List<ChargeEventEntity> chargeEventEntities, ChargeEventEntity event) {
        return TERMINAL_AUTHENTICATION_STATES.contains(event.getStatus())
                && isNotDuplicatePaymentDetailsEvent(chargeEventEntities, event);
    }

    private boolean isNotDuplicatePaymentDetailsEvent(List<ChargeEventEntity> chargeEventEntities, ChargeEventEntity event) {
        return !event.getStatus().equals(AUTHORISATION_SUCCESS)
                || event.getStatus().equals(AUTHORISATION_SUCCESS)
                && !chargeEventEntities
                .stream()
                .map(ChargeEventEntity::getStatus)
                .collect(Collectors.toList()).contains(AUTHORISATION_3DS_REQUIRED);
    }

    private ZonedDateTime getDoNotRetryEmitUntilDate() {
        return doNotRetryEmitUntilDuration == null ? null :
                now().plusSeconds(doNotRetryEmitUntilDuration);
    }
}
