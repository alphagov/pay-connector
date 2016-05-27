package uk.gov.pay.connector.model.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public final class StateTransitions {

    private static final Map<ChargeStatus, List<ChargeStatus>> TRANSITION_TABLE = ImmutableMap.<ChargeStatus, List<ChargeStatus>>builder()

            .put(CREATED,               validTransitions(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, EXPIRED))
            .put(ENTERING_CARD_DETAILS, validTransitions(AUTHORISATION_READY, EXPIRED, USER_CANCELLED, SYSTEM_CANCELLED))
            .put(AUTHORISATION_READY,   validTransitions(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED, AUTHORISATION_ERROR))
            .put(AUTHORISATION_SUCCESS, validTransitions(CAPTURE_READY, SYSTEM_CANCEL_READY, USER_CANCEL_READY, EXPIRE_CANCEL_READY))
            .put(CAPTURE_READY,         validTransitions(CAPTURE_SUBMITTED, CAPTURE_ERROR))
            .put(CAPTURE_SUBMITTED,     validTransitions(CAPTURED)) // can this ever be a capture error?
            .put(EXPIRE_CANCEL_READY,   validTransitions(EXPIRE_CANCEL_FAILED, EXPIRED))
            .put(SYSTEM_CANCEL_READY,   validTransitions(SYSTEM_CANCEL_ERROR, SYSTEM_CANCELLED))
            .put(USER_CANCEL_READY,     validTransitions(USER_CANCEL_ERROR, USER_CANCELLED))
            .build();

    public static boolean transitionTo(ChargeStatus state, ChargeStatus targetState) {
        return TRANSITION_TABLE.getOrDefault(state, emptyList()).contains(targetState);
    }

    private static ImmutableList<ChargeStatus> validTransitions(ChargeStatus... statuses) {
        return ImmutableList.copyOf(statuses);
    }
}
