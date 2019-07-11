package uk.gov.pay.connector.common.model.domain;

import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import org.apache.commons.lang3.tuple.Triple;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.events.AuthorisationCancelled;
import uk.gov.pay.connector.events.AuthorisationRejected;
import uk.gov.pay.connector.events.AuthorisationSucceeded;
import uk.gov.pay.connector.events.CaptureAbandonedAfterTooManyRetries;
import uk.gov.pay.connector.events.CaptureConfirmed;
import uk.gov.pay.connector.events.CaptureError;
import uk.gov.pay.connector.events.CaptureSubmitted;
import uk.gov.pay.connector.events.Event;
import uk.gov.pay.connector.events.GatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.GatewayRequires3dsAuthorisation;
import uk.gov.pay.connector.events.GatewayTimeoutDuringAuthorisation;
import uk.gov.pay.connector.events.PaymentCreated;
import uk.gov.pay.connector.events.PaymentExpired;
import uk.gov.pay.connector.events.PaymentStarted;
import uk.gov.pay.connector.events.ServiceApprovedForCapture;
import uk.gov.pay.connector.events.SystemCancelled;
import uk.gov.pay.connector.events.UnexpectedGatewayErrorDuringAuthorisation;
import uk.gov.pay.connector.events.UnspecifiedEvent;
import uk.gov.pay.connector.events.UserApprovedForCapture;
import uk.gov.pay.connector.events.UserApprovedForCaptureAwaitingServiceApproval;
import uk.gov.pay.connector.events.UserCancelled;

import java.util.Set;
import java.util.stream.Collectors;

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
    private static PaymentGatewayStateTransitions instance;

    public static PaymentGatewayStateTransitions getInstance() {
        if (instance == null) {
            instance = new PaymentGatewayStateTransitions();
        }
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
        graph.putEdgeValue(CREATED, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, EXPIRED, ModelledEvent.of(PaymentExpired.class));

        graph.putEdgeValue(CREATED, ENTERING_CARD_DETAILS, ModelledEvent.of(PaymentStarted.class));
        graph.putEdgeValue(CREATED, SYSTEM_CANCELLED, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_READY, ModelledEvent.none());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_ABORTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, USER_CANCELLED, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ABORTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_TIMEOUT, ModelledEvent.of(GatewayTimeoutDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_UNEXPECTED_ERROR, ModelledEvent.of(UnexpectedGatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_3DS_REQUIRED, ModelledEvent.of(GatewayRequires3dsAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUBMITTED, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_3DS_REQUIRED, ModelledEvent.of(GatewayRequires3dsAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, USER_CANCELLED, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_SUCCESS, ModelledEvent.of(AuthorisationSucceeded.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_REJECTED, ModelledEvent.of(AuthorisationRejected.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_ERROR, ModelledEvent.of(GatewayErrorDuringAuthorisation.class));
        graph.putEdgeValue(AUTHORISATION_3DS_READY, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_CANCELLED, ModelledEvent.of(AuthorisationCancelled.class));
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_APPROVED, ModelledEvent.of(UserApprovedForCapture.class));
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, USER_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, EXPIRE_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, AWAITING_CAPTURE_REQUEST, ModelledEvent.of(UserApprovedForCaptureAwaitingServiceApproval.class));

        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, CAPTURE_APPROVED, ModelledEvent.of(ServiceApprovedForCapture.class));
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, SYSTEM_CANCEL_READY, ModelledEvent.none());
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, EXPIRE_CANCEL_READY, ModelledEvent.none());

        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_ERROR, ModelledEvent.of(CaptureAbandonedAfterTooManyRetries.class));
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_READY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_ERROR, ModelledEvent.of(CaptureAbandonedAfterTooManyRetries.class));
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_SUBMITTED, ModelledEvent.of(CaptureSubmitted.class));
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_ERROR, ModelledEvent.of(CaptureError.class));
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_APPROVED_RETRY, ModelledEvent.none());
        graph.putEdgeValue(CAPTURE_READY, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));
        graph.putEdgeValue(CAPTURE_SUBMITTED, CAPTURED, ModelledEvent.of(CaptureConfirmed.class));
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_FAILED, ModelledEvent.of(PaymentExpired.class));

        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRE_CANCEL_FAILED, ModelledEvent.of(PaymentExpired.class));
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_SUBMITTED, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_ERROR, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCELLED, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED, ModelledEvent.of(SystemCancelled.class));
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_SUBMITTED, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_ERROR, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCELLED, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, ModelledEvent.of(UserCancelled.class));
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCELLED, ModelledEvent.of(UserCancelled.class));
        
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
                        graph.edgeValue(edge.nodeU(), edge.nodeV()).map(e -> e.toString()).orElse("")))
                .collect(Collectors.toSet());
    }

    public static boolean isValidTransition(ChargeStatus state, ChargeStatus targetState, Event event) {
        return getInstance().isValidTransitionImpl(state, targetState, event);
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

    }
}
