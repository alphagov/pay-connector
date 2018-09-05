package uk.gov.pay.connector.model.domain;

import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Set;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AWAITING_CAPTURE_REQUEST;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;

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

    private static ImmutableValueGraph<ChargeStatus, String> buildGraph() {
        MutableValueGraph<ChargeStatus, String> graph = ValueGraphBuilder
                .directed()
                .build();

        graph.putEdgeValue(CREATED, EXPIRED, "ChargeExpiryService");
        graph.putEdgeValue(ENTERING_CARD_DETAILS, EXPIRED, "ChargeExpiryService");
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, EXPIRED, "ChargeExpiryService");
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRED, "ChargeExpiryService");
        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRED, "ChargeExpiryService");

        graph.putEdgeValue(CREATED, ENTERING_CARD_DETAILS, "frontend: POST /frontend/charges/{chargeId}/status");
        graph.putEdgeValue(CREATED, SYSTEM_CANCELLED, "");
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_READY, "user submitted card details");
        graph.putEdgeValue(ENTERING_CARD_DETAILS, AUTHORISATION_ABORTED, "");
        graph.putEdgeValue(ENTERING_CARD_DETAILS, USER_CANCELLED, "user clicked cancel");
        graph.putEdgeValue(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, "");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUCCESS, "Gateway response: AUTHORISED");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_REJECTED, "Gateway response: REJECTED");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_ERROR, "Gateway error");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_TIMEOUT, "Gateway timeout");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_UNEXPECTED_ERROR, "Gateway response: unexpected error");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_3DS_REQUIRED, "Gateway response: 3ds required");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_CANCELLED, "Gateway response: cancelled");
        graph.putEdgeValue(AUTHORISATION_READY, AUTHORISATION_SUBMITTED, "Epdq only: WAITING_EXTERNAL(50)");
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_SUCCESS, "");
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_REJECTED, "");
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_ERROR, "");
        graph.putEdgeValue(AUTHORISATION_SUBMITTED, AUTHORISATION_3DS_REQUIRED, "");
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, AUTHORISATION_3DS_READY, "");
        graph.putEdgeValue(AUTHORISATION_3DS_REQUIRED, USER_CANCELLED, "");
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_SUCCESS, "");
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_REJECTED, "");
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_ERROR, "");
        graph.putEdgeValue(AUTHORISATION_3DS_READY, AUTHORISATION_CANCELLED, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_APPROVED, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, CAPTURE_READY, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, SYSTEM_CANCEL_READY, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, USER_CANCEL_READY, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, EXPIRE_CANCEL_READY, "");
        graph.putEdgeValue(AUTHORISATION_SUCCESS, AWAITING_CAPTURE_REQUEST, "");
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, CAPTURE_APPROVED, "");
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, SYSTEM_CANCEL_READY, "");
        graph.putEdgeValue(AWAITING_CAPTURE_REQUEST, EXPIRE_CANCEL_READY, "");
        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_READY, "");
        graph.putEdgeValue(CAPTURE_APPROVED, CAPTURE_ERROR, "");
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_READY, "");
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURE_ERROR, "");
        graph.putEdgeValue(CAPTURE_APPROVED_RETRY, CAPTURED, "");
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_SUBMITTED, "");
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_ERROR, "");
        graph.putEdgeValue(CAPTURE_READY, CAPTURE_APPROVED_RETRY, "");
        graph.putEdgeValue(CAPTURE_SUBMITTED, CAPTURED, "");
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_SUBMITTED, "");
        graph.putEdgeValue(EXPIRE_CANCEL_READY, EXPIRE_CANCEL_FAILED, "");

        graph.putEdgeValue(EXPIRE_CANCEL_SUBMITTED, EXPIRE_CANCEL_FAILED, "");
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_SUBMITTED, "");
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCEL_ERROR, "");
        graph.putEdgeValue(SYSTEM_CANCEL_READY, SYSTEM_CANCELLED, "");
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR, "");
        graph.putEdgeValue(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLED, "");
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_SUBMITTED, "");
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCEL_ERROR, "");
        graph.putEdgeValue(USER_CANCEL_READY, USER_CANCELLED, "");
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, "");
        graph.putEdgeValue(USER_CANCEL_SUBMITTED, USER_CANCELLED, "");

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
                        graph.edgeValue(edge.nodeU(), edge.nodeV()
                    )))
                .collect(Collectors.toSet());
    }

    public static boolean isValidTransition(ChargeStatus state, ChargeStatus targetState) {
        return getInstance().isValidTransitionImpl(state, targetState);
    }

    private boolean isValidTransitionImpl(ChargeStatus state, ChargeStatus targetState) {
        return graph.edgeValueOrDefault(state, targetState, null) != null;
    }
}
