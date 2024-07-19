package uk.gov.pay.connector.common.model.domain;

import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import org.apache.commons.lang3.tuple.Triple;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.UnspecifiedEvent;
import uk.gov.pay.connector.events.model.charge.AuthorisationCancelled;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasMissing;
import uk.gov.pay.connector.events.model.charge.AuthorisationErrorCheckedWithGatewayChargeWasRejected;
import uk.gov.pay.connector.events.model.charge.AuthorisationRejected;
import uk.gov.pay.connector.events.model.charge.AuthorisationSucceeded;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExpirationSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceFailed;
import uk.gov.pay.connector.events.model.charge.CancelByExternalServiceSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelByUserFailed;
import uk.gov.pay.connector.events.model.charge.CancelByUserSubmitted;
import uk.gov.pay.connector.events.model.charge.CancelledByExpiration;
import uk.gov.pay.connector.events.model.charge.CancelledByExternalService;
import uk.gov.pay.connector.events.model.charge.CancelledByUser;
import uk.gov.pay.connector.events.model.charge.CancelledWithGatewayAfterAuthorisationError;
import uk.gov.pay.connector.events.model.charge.CaptureAbandonedAfterTooManyRetries;
import uk.gov.pay.connector.events.model.charge.CaptureConfirmed;
import uk.gov.pay.connector.events.model.charge.CaptureErrored;
import uk.gov.pay.connector.events.model.charge.CaptureSubmitted;
import uk.gov.pay.connector.events.model.charge.GatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.model.charge.GatewayTimeoutDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.PaymentCreated;
import uk.gov.pay.connector.events.model.charge.PaymentExpired;
import uk.gov.pay.connector.events.model.charge.PaymentNotificationCreated;
import uk.gov.pay.connector.events.model.charge.PaymentStarted;
import uk.gov.pay.connector.events.model.charge.QueuedForAuthorisationWithUserNotPresent;
import uk.gov.pay.connector.events.model.charge.QueuedForCapture;
import uk.gov.pay.connector.events.model.charge.ServiceApprovedForCapture;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationErrorToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.StatusCorrectedToCapturedToMatchGatewayStatus;
import uk.gov.pay.connector.events.model.charge.UnexpectedGatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.model.charge.UserApprovedForCapture;
import uk.gov.pay.connector.events.model.charge.UserApprovedForCaptureAwaitingServiceApproval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_CHARGE_MISSING;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_ERROR_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_USER_NOT_PRESENT_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.PAYMENT_NOTIFICATION_CREATED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_QUEUED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.UNDEFINED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

public class PaymentGatewayStateTransitions {
    private static final PaymentGatewayStateTransitions instance = new PaymentGatewayStateTransitions();

    private static Map<ChargeStatus, ModelledTypedEvent> eventsForForceUpdatingStatus = Map.of(
            CAPTURED, new ModelledTypedEvent<>(StatusCorrectedToCapturedToMatchGatewayStatus.class),
            AUTHORISATION_REJECTED, new ModelledTypedEvent<>(StatusCorrectedToAuthorisationRejectedToMatchGatewayStatus.class),
            AUTHORISATION_ERROR, new ModelledTypedEvent<>(StatusCorrectedToAuthorisationErrorToMatchGatewayStatus.class)
    );

    public static PaymentGatewayStateTransitions getInstance() {
        return instance;
    }

    private ImmutableValueGraph<ChargeStatus, ModelledEvent> graph;

    private PaymentGatewayStateTransitions() {
        graph = buildGraph();
    }

    private static ImmutableValueGraph<ChargeStatus, ModelledEvent> buildGraph() {
        MutableValueGraph<ChargeStatus, ModelledEvent> graph = ValueGraphBuilder
                .directed()
                .build();

        graph.putEdgeValue(UNDEFINED, CREATED, ModelledEvent.of(PaymentCreated.class));
        graph.putEdgeValue(UNDEFINED, PAYMENT_NOTIFICATION_CREATED, ModelledEvent.of(PaymentNotificationCreated.class));
        graph.putEdgeValue(CREATED, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(AUTHORISATION_READY, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, EXPIRED, ModelledEvent.of(PaymentExpired.class));

        graph.putEdgeValue(CREATED, ENTERING_CARD_DETAILS, ModelledEvent.of(PaymentStarted.class));
        graph.putEdgeValue(CREATED, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));
        graph.putEdgeValue(CREATED, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(CREATED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(CREATED, AUTHORISATION_READY, ModelledEvent.none());
        graph.putEdgeValue(CREATED, AUTHORISATION_USER_NOT_PRESENT_QUEUED, ModelledEvent.of(QueuedForAuthorisationWithUserNotPresent.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_READY, ModelledEvent.none());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_ABORTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ABORTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_TIMEOUT, ModelledEvent.of(GatewayTimeoutDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_UNEXPECTED_ERROR, ModelledEvent.of(UnexpectedGatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_3DS_REQUIRED, ModelledEvent.of(GatewayRequires3dsAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUBMITTED, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_3DS_REQUIRED, ModelledEvent.of(GatewayRequires3dsAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, USER_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_3DS_REQUIRED, ModelledEvent.of(GatewayRequires3dsAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, USER_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(PAYMENT_NOTIFICATION_CREATED, CAPTURE_SUBMITTED, ModelledEvent.of(CaptureSubmitted.class));
        graph.putEdgeValue(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(PAYMENT_NOTIFICATION_CREATED, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_APPROVED, ModelledEvent.of(UserApprovedForCapture.class));
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_QUEUED, ModelledEvent.of(QueuedForCapture.class));
        graph.putEdgeValue(AUTHORISATION_SUCCESS, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, USER_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, AWAITING_CAPTURE_REQUEST, ModelledEvent.of(UserApprovedForCaptureAwaitingServiceApproval.class));

        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, CAPTURE_APPROVED, ModelledEvent.of(ServiceApprovedForCapture.class));
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, EXPIRE_CANCEL_READY, ModelledEvent.none());

        graph.putEdgeValue(AUTHORISATION_USER_NOT_PRESENT_QUEUED, AUTHORISATION_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_QUEUED, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_QUEUED, CAPTURE_ERROR, ModelledEvent.of(CaptureErrored.class));

        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_ERROR, ModelledEvent.of(CaptureAbandonedAfterTooManyRetries.class));
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_ERROR, ModelledEvent.of(CaptureAbandonedAfterTooManyRetries.class));
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));

        graph.putEdgeValue(CAPTURE_READY, CAPTURE_SUBMITTED, ModelledEvent.of(CaptureSubmitted.class));
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_ERROR, ModelledEvent.of(CaptureErrored.class));
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_APPROVED_RETRY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_READY, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));

        graph.putEdgeValue(CAPTURE_SUBMITTED, CAPTURE_ERROR, ModelledEvent.of(CaptureErrored.class));
        graph.putEdgeValue(CAPTURE_SUBMITTED, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));

        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, ModelledEvent.of(CancelByExpirationSubmitted.class));
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_FAILED, ModelledEvent.of(CancelByExpirationFailed.class));
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRED, ModelledEvent.of(CancelledByExpiration.class));
        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRE_CANCEL_FAILED, ModelledEvent.of(CancelByExpirationFailed.class));
        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRED, ModelledEvent.of(CancelledByExpiration.class));

        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_SUBMITTED, ModelledEvent.of(CancelByExternalServiceSubmitted.class));
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_ERROR, ModelledEvent.of(CancelByExternalServiceFailed.class));
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR, ModelledEvent.of(CancelByExternalServiceFailed.class));
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED, ModelledEvent.of(CancelledByExternalService.class));

        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_SUBMITTED, ModelledEvent.of(CancelByUserSubmitted.class));
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_ERROR, ModelledEvent.of(CancelByUserFailed.class));
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, ModelledEvent.of(CancelByUserFailed.class));
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCELLED, ModelledEvent.of(CancelledByUser.class));

        graph.putEdgeValue(AUTHORISATION_ERROR, AUTHORISATION_ERROR_CANCELLED, ModelledEvent.of(CancelledWithGatewayAfterAuthorisationError.class));
        graph.putEdgeValue(AUTHORISATION_ERROR, AUTHORISATION_ERROR_REJECTED, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasRejected.class));
        graph.putEdgeValue(AUTHORISATION_ERROR, AUTHORISATION_ERROR_CHARGE_MISSING, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasMissing.class));
        graph.putEdgeValue(AUTHORISATION_UNEXPECTED_ERROR, AUTHORISATION_ERROR_CANCELLED, ModelledEvent.of(CancelledWithGatewayAfterAuthorisationError.class));
        graph.putEdgeValue(AUTHORISATION_UNEXPECTED_ERROR, AUTHORISATION_ERROR_REJECTED, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasRejected.class));
        graph.putEdgeValue(AUTHORISATION_UNEXPECTED_ERROR, AUTHORISATION_ERROR_CHARGE_MISSING, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasMissing.class));
        graph.putEdgeValue(AUTHORISATION_TIMEOUT, AUTHORISATION_ERROR_CANCELLED, ModelledEvent.of(CancelledWithGatewayAfterAuthorisationError.class));
        graph.putEdgeValue(AUTHORISATION_TIMEOUT, AUTHORISATION_ERROR_REJECTED, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasRejected.class));
        graph.putEdgeValue(AUTHORISATION_TIMEOUT, AUTHORISATION_ERROR_CHARGE_MISSING, ModelledEvent.of(AuthorisationErrorCheckedWithGatewayChargeWasMissing.class));


        return ImmutableValueGraph.copyOf(graph);
    }

    public Set<ChargeStatus> allStatuses() {
        return graph.nodes();
    }

    public Set<Triple<ChargeStatus, ChargeStatus, String>> allTransitions() {
        return graph
                .edges()
                .stream()
                .map(edge -> Triple.of(
                        edge.nodeU(),
                        edge.nodeV(),
                        graph.edgeValue(edge.nodeU(), edge.nodeV()).map(Object::toString).orElse("")))
                .collect(Collectors.toSet());
    }

    public <T extends Event> Optional<Class<T>> getEventForTransition(ChargeStatus fromStatus, ChargeStatus toStatus) {
        return graph.edgeValue(fromStatus, toStatus)
                .map(modelledEvent -> {
                    try {
                        ModelledTypedEvent<T> modelledTypedEvent = (ModelledTypedEvent) modelledEvent;
                        return modelledTypedEvent.getClazz();
                    } catch (ClassCastException e) {
                        return null;
                    }
                });
    }

    public <T extends Event> List<ChargeStatus> getNextStatus(ChargeStatus fromStatus) {
        return new ArrayList<>(graph.successors(fromStatus));
    }

    public static List<Class> getAllEventsResultingInTerminalState() {
        return getInstance().graph.edges()
                .stream()
                .filter(endpointPair -> getInstance().graph.successors(endpointPair.nodeV()).isEmpty())
                .map(getInstance().graph::edgeValue)
                .flatMap(Optional::stream)
                .map(me -> (ModelledTypedEvent) me)
                .map(ModelledTypedEvent::getClazz)
                .distinct()
                .collect(Collectors.toList());
    }

    public static boolean isValidTransition(ChargeStatus state, ChargeStatus targetState, Event event) {
        return getInstance().isValidTransitionImpl(state, targetState, event);
    }

    public Optional<ChargeStatus> getIntermediateChargeStatus(ChargeStatus fromStatus, ChargeStatus toStatus) {

        // Multiple intermediate states possible : (EXPIRE CANCEL READY, AUTHORISATION 3DS READY)
        if (fromStatus.equals(AUTHORISATION_3DS_REQUIRED) && toStatus.equals(EXPIRED)) {
            return Optional.of(EXPIRE_CANCEL_READY);
        }

        // Multiple intermediate states possible : (CAPTURE READY, CAPTURE APPROVED)
        if (fromStatus.equals(AUTHORISATION_SUCCESS) && toStatus.equals(CAPTURE_ERROR)) {
            return Optional.of(CAPTURE_READY);
        }

        return graph.successors(fromStatus)
                .stream()
                .filter(chargeStatus -> graph.predecessors(toStatus).contains(chargeStatus))
                .findFirst();
    }

    public static <T extends Event> Optional<Class<T>> getEventForForceUpdate(ChargeStatus targetChargeStatus) {
        return Optional.ofNullable(eventsForForceUpdatingStatus.get(targetChargeStatus))
                .map(modelledTypedEvent -> modelledTypedEvent.getClazz());
    }

    private boolean isValidTransitionImpl(ChargeStatus state, ChargeStatus targetState, Event event) {
        return graph.edgeValue(state, targetState)
                .map(modelledEvent ->
                        (event instanceof UnspecifiedEvent) || modelledEvent.permits(event)
                )
                .orElse(false);
    }

    private static abstract class ModelledEvent {
        public abstract boolean permits(Event event);

        public static <T extends Event> ModelledEvent of(Class<T> clazz) {
            return new ModelledTypedEvent(clazz);
        }

        public static ModelledEvent none() {
            return new NoEvent();
        }

    }

    private static class NoEvent extends ModelledEvent {
        @Override
        public boolean permits(Event event) {
            return false;
        }

        @Override
        public String toString() {
            return "";
        }
    }

    private static class ModelledTypedEvent<T extends Event> extends ModelledEvent {
        private final Class<T> clazz;

        public ModelledTypedEvent(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public boolean permits(Event event) {
            return clazz.isInstance(event);
        }

        @Override
        public String toString() {
            return clazz.getSimpleName();
        }

        public Class<T> getClazz() {
            return clazz;
        }
    }
}
