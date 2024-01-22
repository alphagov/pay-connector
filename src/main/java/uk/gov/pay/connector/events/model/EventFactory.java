package uk.gov.pay.connector.events.model;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.service.ChargeService;
import uk.gov.pay.connector.chargeevent.dao.ChargeEventDao;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.model.domain.PaymentGatewayStateTransitions;
import uk.gov.pay.connector.events.eventdetails.refund.RefundEventWithGatewayTransactionIdDetails;
import uk.gov.pay.connector.events.exception.EventCreationException;
import uk.gov.pay.connector.events.model.charge.AuthorisationRejected;
import uk.gov.pay.connector.events.model.charge.BackfillerRecreatedUserEmailCollected;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByUserSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelledByUser;
import uk.gov.pay.connector.events.model.charge.CancelledWithGatewayAfterAuthorisationError;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayTimeoutDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentDetailsEntered;
import uk.gov.pay.connector.events.model.charge.PaymentEvent;
import uk.gov.pay.connector.events.model.charge.PaymentNotificationCreated;
import uk.gov.pay.connector.events.model.charge.RefundAvailabilityUpdated;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationErrorToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.UnexpectedGatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByService;
import uk.gov.pay.connector.events.model.refund.RefundCreatedByUser;
import uk.gov.pay.connector.events.model.refund.RefundError;
import uk.gov.pay.connector.events.model.refund.RefundEvent;
import uk.gov.pay.connector.gateway.PaymentProviders;
import uk.gov.pay.connector.queue.statetransition.PaymentStateTransition;
import uk.gov.pay.connector.queue.statetransition.RefundStateTransition;
import uk.gov.pay.connector.queue.statetransition.StateTransition;
import uk.gov.pay.connector.refund.dao.RefundDao;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.service.RefundService;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventFactory {
    private final ChargeService chargeService;
    private final RefundDao refundDao;
    private final RefundService refundService;
    private final PaymentProviders paymentProviders;
    private final ChargeEventDao chargeEventDao;
    private static final List<Class<? extends Event>> EVENTS_AFFECTING_REFUNDABILITY = List.of(
            RefundCreatedByUser.class,
            RefundCreatedByService.class,
            RefundError.class,
            PaymentCreated.class,
            CaptureSubmitted.class,
            GatewayErrorDuringAuthorisation.class,
            GatewayTimeoutDuringAuthorisation.class,
            UnexpectedGatewayErrorDuringAuthorisation.class,
            StatusCorrectedToAuthorisationErrorToMatchGatewayStatus.class,
            StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus.class,
            StatusCorrectedToCapturedToMatchGatewayStatus.class,
            CancelByExternalServiceSubmitted.class,
            CancelByExpirationSubmitted.class,
            CancelByUserSubmitted.class
    );

    private static final List<Class> EVENTS_LEADING_TO_TERMINAL_STATE =
            PaymentGatewayStateTransitions.getAllEventsResultingInTerminalState();

    @Inject
    public EventFactory(ChargeService chargeService, RefundDao refundDao, RefundService refundService, ChargeEventDao chargeEventDao, PaymentProviders paymentProviders) {
        this.chargeService = chargeService;
        this.refundDao = refundDao;
        this.refundService = refundService;
        this.chargeEventDao = chargeEventDao;
        this.paymentProviders = paymentProviders;
    }

    public List<Event> createEvents(StateTransition stateTransition) throws EventCreationException {
        if (stateTransition instanceof PaymentStateTransition) {
            PaymentStateTransition paymentStateTransition = (PaymentStateTransition) stateTransition;
            return createPaymentEvents(paymentStateTransition);
        } else if (stateTransition instanceof RefundStateTransition) {
            RefundStateTransition refundStateTransition = (RefundStateTransition) stateTransition;
            return createRefundEvents(refundStateTransition);
        } else {
            throw new EventCreationException(stateTransition.getIdentifier(), "Failed to create StateTransition event because event is not an instance of PaymentStateTransition or RefundStateTransition");
        }
    }

    private List<Event> createPaymentEvents(PaymentStateTransition paymentStateTransition) throws EventCreationException {
        ChargeEventEntity chargeEvent = chargeEventDao.findById(ChargeEventEntity.class, paymentStateTransition.getChargeEventId())
                .orElseThrow(() -> new EventCreationException(String.valueOf(paymentStateTransition.getChargeEventId()), "Failed to create PaymentStateTransition event because the associated charge event could not be found"));

        PaymentEvent paymentEvent = createPaymentEvent(chargeEvent, paymentStateTransition.getStateTransitionEventClass());

        Optional<Event> refundAvailabilityEvent = createRefundAvailabilityUpdatedEvent(
                Charge.from(chargeEvent.getChargeEntity()),
                chargeEvent.getUpdated().toInstant(),
                paymentStateTransition.getStateTransitionEventClass()
        );

        return Stream.of(Optional.of(paymentEvent), refundAvailabilityEvent)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    private List<Event> createRefundEvents(RefundStateTransition refundStateTransition) throws EventCreationException {
        RefundHistory refundHistory = refundDao.getRefundHistoryByRefundExternalIdAndRefundStatus(
                        refundStateTransition.getRefundExternalId(),
                        refundStateTransition.getRefundStatus())
                .orElseThrow(() -> new EventCreationException(refundStateTransition.getIdentifier(), "Failed to create RefundStateTransition event because refund history could not be found"));
        Charge charge = chargeService.findCharge(refundHistory.getChargeExternalId())
                .orElseThrow(() -> new EventCreationException(refundHistory.getChargeExternalId(), "Failed to create RefundStateTransition event because the charge could not be found"));

        Event refundEvent = createRefundEvent(refundHistory, refundStateTransition.getStateTransitionEventClass(),
                charge);
        Optional<Event> refundAvailabilityEvent = createRefundAvailabilityUpdatedEvent(
                charge,
                refundHistory.getHistoryStartDate().toInstant(),
                refundStateTransition.getStateTransitionEventClass()
        );

        return Stream.of(Optional.of(refundEvent), refundAvailabilityEvent)
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
    }

    public static PaymentEvent createPaymentEvent(ChargeEventEntity chargeEvent, Class<? extends PaymentEvent> eventClass) {
        try {
            if (eventClass == PaymentCreated.class) {
                return PaymentCreated.from(chargeEvent);
            } else if (eventClass == PaymentDetailsEntered.class) {
                return PaymentDetailsEntered.from(chargeEvent);
            } else if (eventClass == CaptureSubmitted.class) {
                return CaptureSubmitted.from(chargeEvent);
            } else if (eventClass == CaptureConfirmed.class) {
                return CaptureConfirmed.from(chargeEvent);
            } else if (eventClass == PaymentNotificationCreated.class) {
                return PaymentNotificationCreated.from(chargeEvent);
            } else if (eventClass == CancelledByUser.class) {
                return CancelledByUser.from(chargeEvent);
            } else if (eventClass == GatewayRequires3dsAuthorisation.class) {
                return GatewayRequires3dsAuthorisation.from(chargeEvent);
            } else if (eventClass == BackfillerRecreatedUserEmailCollected.class) {
                return BackfillerRecreatedUserEmailCollected.from(chargeEvent.getChargeEntity());
            } else if (eventClass == StatusCorrectedToCapturedToMatchGatewayStatus.class) {
                return StatusCorrectedToCapturedToMatchGatewayStatus.from(chargeEvent);
            } else if (eventClass == CancelledWithGatewayAfterAuthorisationError.class) {
                return CancelledWithGatewayAfterAuthorisationError.from(chargeEvent);
            } else if (eventClass == AuthorisationRejected.class) {
                return AuthorisationRejected.from(chargeEvent);
            } else {
                return eventClass.getConstructor(String.class,
                        boolean.class, Long.class, String.class, Instant.class).newInstance(
                        chargeEvent.getChargeEntity().getServiceId(),
                        chargeEvent.getChargeEntity().getGatewayAccount().isLive(),
                        chargeEvent.getChargeEntity().getGatewayAccount().getId(),
                        chargeEvent.getChargeEntity().getExternalId(),
                        chargeEvent.getUpdated().toInstant()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct payment event: %s", eventClass));
        }
    }

    public static Event createRefundEvent(RefundHistory refundHistory, Class<? extends RefundEvent> eventClass, Charge charge) {
        try {
            if (eventClass == RefundCreatedByService.class) {
                return RefundCreatedByService.from(refundHistory, charge);
            } else if (eventClass == RefundCreatedByUser.class) {
                return RefundCreatedByUser.from(refundHistory, charge);
            } else {
                return eventClass.getConstructor(String.class, boolean.class, Long.class, String.class, String.class,
                        RefundEventWithGatewayTransactionIdDetails.class, Instant.class).newInstance(
                        charge.getServiceId(),
                        charge.isLive(),
                        charge.getGatewayAccountId(),
                        refundHistory.getExternalId(),
                        refundHistory.getChargeExternalId(),
                        new RefundEventWithGatewayTransactionIdDetails(refundHistory.getGatewayTransactionId()),
                        refundHistory.getHistoryStartDate().toInstant()
                );
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new IllegalArgumentException(String.format("Could not construct refund event: %s", eventClass));
        }
    }

    private Optional<Event> createRefundAvailabilityUpdatedEvent(Charge charge, Instant eventTimestamp, Class eventClass) {
        if (EVENTS_AFFECTING_REFUNDABILITY.contains(eventClass) || EVENTS_LEADING_TO_TERMINAL_STATE.contains(eventClass)) {
            RefundAvailabilityUpdated refundAvailabilityUpdatedEvent = chargeService.createRefundAvailabilityUpdatedEvent(charge, eventTimestamp);
            return Optional.of(refundAvailabilityUpdatedEvent);
        }

        return Optional.empty();
    }
}
