package uk.gov.pay.connector.common.model.domain;

import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import org.apache.commons.lang3.tuple.Triple;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

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

    private ImmutableValueGraph<ChargeStatus, String> graph;

    private PaymentGatewayStateTransitions() {
        graph = buildGraph();
    }

    private static class Event {}
    private static class SalientEvent extends Event {}
    private static class BoringEvent extends Event {}
    
    private static class ExpiredEvent extends SalientEvent {}
    
    private static class UserVistedPaymentPageEvent extends BoringEvent  {}
    
    private static class DependentServiceCancelledEvent extends SalientEvent  {}
    
    private static class AuthAbortDueTo3dsMisconfigurationEvent extends SalientEvent {}
    private static class UserCancelledEvent extends SalientEvent {}
    private static class AuthorisationSuccessEvent extends SalientEvent {}
    private static class AuthorisationRejectedEvent extends SalientEvent {}
    private static class GatewayErrorDuringAuthorisation extends SalientEvent {}
    private static class GatewayTimeoutDuringAuthorisation extends SalientEvent {}
    private static class UnexpectedGatewayErrorDuringAuthorisation extends SalientEvent {}
    private static class GatewayRequiresThreeDSecure extends SalientEvent {}
    private static class GatewayResponseCancelledEvent extends Event {}
    private static class EpdqWaitingExternal extends Event {}
    private static class AuthSuccess extends SalientEvent {}
    private static class AuthRejected extends SalientEvent {}
    private static class GatewayError extends SalientEvent {}
    private static class UserApprovedPaymentCapture extends SalientEvent {}
    private static class UserApprovedPaymentForCaptureAwaitingDependentServiceConfirmationEvent extends SalientEvent {}
    private static class DependentServiceRequestedCapture extends Event { }
    private static class CaptureErrorEvent extends Event { }
    private static class CapturedEvent extends Event { }
    private static class SubmittedForCaptureEvent extends Event { }
    private static class CaptureFailedQueingForRetryEvent extends Event { }
    private static class SubmittedForCancellationEvent extends Event { }
    private static class CancelFailedEvent extends Event { }
    private static class DependentServiceCancellationRequestSubmitted extends Event { }
    private static class DependentServiceCancellationRequestError extends Event { }
    private static class DependentServiceCancellationRequestSucceeded extends Event { }
    private static class UserCancellationSubmitted extends Event { }
    private static class UserCancellationError extends Event { }
    
    
    private static ImmutableValueGraph<ChargeStatus, Event> buildGraph() {
        MutableValueGraph<ChargeStatus, Event> graph = ValueGraphBuilder
                .directed()
                .build();

        graph.putEdgeValue(CREATED, EXPIRED, new ExpiredEvent());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, EXPIRED, new ExpiredEvent());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRED, new ExpiredEvent());
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRED, new ExpiredEvent());
        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRED, new ExpiredEvent());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, EXPIRED, new ExpiredEvent());

        graph.putEdgeValue(CREATED, ENTERING_CARD_DETAILS, new UserVistedPaymentPageEvent());
        graph.putEdgeValue(CREATED, SYSTEM_CANCELLED, new DependentServiceCancelledEvent());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_ABORTED, new AuthAbortDueTo3dsMisconfigurationEvent());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, USER_CANCELLED, new UserCancelledEvent());
        graph.putEdgeValue(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, new DependentServiceCancelledEvent());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ABORTED, new AuthAbortDueTo3dsMisconfigurationEvent());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUCCESS, new AuthorisationSuccessEvent());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_REJECTED, new AuthorisationRejectedEvent());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ERROR, new GatewayErrorDuringAuthorisation());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_TIMEOUT, new GatewayTimeoutDuringAuthorisation());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_UNEXPECTED_ERROR, new UnexpectedGatewayErrorDuringAuthorisation());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_3DS_REQUIRED, new GatewayRequiresThreeDSecure());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_CANCELLED, new GatewayResponseCancelledEvent());
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUBMITTED, new EpdqWaitingExternal());
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS, new AuthSuccess());
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_REJECTED, new AuthRejected());
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_ERROR, new GatewayError());
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_3DS_REQUIRED, new GatewayRequiresThreeDSecure());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_REJECTED, new AuthRejected());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_CANCELLED, new GatewayResponseCancelledEvent());
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, USER_CANCELLED, new UserCancelledEvent());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_SUCCESS, new AuthSuccess());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_REJECTED, new AuthRejected());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_ERROR, new GatewayError());
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_CANCELLED, new GatewayResponseCancelledEvent());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_APPROVED, new UserApprovedPaymentCapture());
        graph.putEdgeValue(AUTHORISATION_SUCCESS, AWAITING_CAPTURE_REQUEST, new UserApprovedPaymentForCaptureAwaitingDependentServiceConfirmationEvent());

        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, CAPTURE_APPROVED, new DependentServiceRequestedCapture());
        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_ERROR, new CaptureErrorEvent());
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_ERROR, new CaptureErrorEvent()); 
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURED, new CapturedEvent());
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_SUBMITTED, new SubmittedForCaptureEvent());
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_APPROVED_RETRY, new CaptureFailedQueingForRetryEvent());
        graph.putEdgeValue(CAPTURE_READY, CAPTURED, new CapturedEvent());
        graph.putEdgeValue(CAPTURE_SUBMITTED, CAPTURED, new CapturedEvent());
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, new SubmittedForCancellationEvent());
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_FAILED, new CancelFailedEvent());

        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRE_CANCEL_FAILED, new CancelFailedEvent());
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_SUBMITTED, new DependentServiceCancellationRequestSubmitted());
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_ERROR, new DependentServiceCancellationRequestError());
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCELLED, new DependentServiceCancellationRequestSucceeded());
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR, new DependentServiceCancellationRequestError());
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED, new DependentServiceCancellationRequestSucceeded());
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_SUBMITTED, new UserCancellationSubmitted());
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_ERROR, new UserCancellationError());
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCELLED, new UserCancelledEvent());
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, new UserCancellationError());
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCELLED, new UserCancelledEvent());

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
                        graph.edgeValueOrDefault(edge.nodeU(), edge.nodeV(), "")))
                .collect(Collectors.toSet());
    }

    public static boolean isValidTransition(ChargeStatus state, ChargeStatus targetState) {
        return getInstance().isValidTransitionImpl(state, targetState);
    }

    private boolean isValidTransitionImpl(ChargeStatus state, ChargeStatus targetState) {
        return graph.edgeValueOrDefault(state, targetState, null) != null;
    }

}
