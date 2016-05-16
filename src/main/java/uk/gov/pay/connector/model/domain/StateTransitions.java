package uk.gov.pay.connector.model.domain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class StateTransitions {

    private static final Map<ChargeStatus, List<ChargeStatus>> TRANSITION_TABLE = ImmutableMap.<ChargeStatus, List<ChargeStatus>>builder()

            .put(CREATED,               validTransitions(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, EXPIRED))
            //ENTERING_CARD_DETAILS -> ENTERING_CARD_DETAILS currently seems harmless, so including as a valid transition
            .put(ENTERING_CARD_DETAILS, validTransitions(ENTERING_CARD_DETAILS, AUTHORISATION_READY, EXPIRED, USER_CANCELLED, SYSTEM_CANCELLED))
            .put(AUTHORISATION_READY,   validTransitions(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED, AUTHORISATION_ERROR))
            .put(AUTHORISATION_SUCCESS, validTransitions(CAPTURE_READY, CANCEL_READY, EXPIRE_CANCEL_PENDING))
            .put(CAPTURE_READY,         validTransitions(CAPTURE_SUBMITTED, CAPTURE_ERROR))
            .put(CAPTURE_SUBMITTED,     validTransitions(CAPTURED)) // can this ever be a capture error?
            /**
             * FIXME: EXPIRE_CANCEL_PENDING --> SYSTEM_CANCELLED and EXPIRE_CANCEL_PENDING --> CANCEL_READY are wrong.
             *   temporarily allowing to keep the current functionality working
             */
            .put(EXPIRE_CANCEL_PENDING, validTransitions(EXPIRE_CANCEL_FAILED, SYSTEM_CANCELLED, CANCEL_READY, EXPIRED))
            .put(CANCEL_READY,          validTransitions(CANCEL_ERROR, SYSTEM_CANCELLED, USER_CANCEL_ERROR, USER_CANCELLED))

            /**
             * FIXME: SYSTEM_CANCELLED --> EXPIRED is wrong.
             *   temporarily allowing to keep the current functionality working
             */
            .put(SYSTEM_CANCELLED,      validTransitions(EXPIRED))

            .build();

    public static Boolean transitionTo(ChargeStatus state, ChargeStatus targetState) {
        return TRANSITION_TABLE.getOrDefault(state, emptyList()).contains(targetState);
    }

    private static ImmutableList<ChargeStatus> validTransitions(ChargeStatus... statuses) {
        return ImmutableList.copyOf(statuses);
    }
}
