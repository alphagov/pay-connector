package uk.gov.pay.connector.common.exception;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;

import static java.lang.String.format;

public class InvalidForceStateTransitionException extends IllegalStateException {

    public InvalidForceStateTransitionException(ChargeStatus currentState, ChargeStatus targetState) {
        super(format("Cannot force charge state transition [%s] -> [%s].", currentState.getValue(), targetState.getValue()));
    }
}
