package uk.gov.pay.connector.service;

import uk.gov.pay.connector.model.domain.ChargeStatus;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class StatusFlow {

    public static final StatusFlow USER_CANCELLATION_FLOW = new StatusFlow("User Cancellation",
            new ChargeStatus[]{
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUCCESS
            },
            USER_CANCEL_READY,
            USER_CANCELLED,
            USER_CANCEL_ERROR
    );

    public static final StatusFlow SYSTEM_CANCELLATION_FLOW = new StatusFlow("System Cancellation",
            new ChargeStatus[]{
                    CREATED,
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUCCESS
            },
            SYSTEM_CANCEL_READY,
            SYSTEM_CANCELLED,
            SYSTEM_CANCEL_ERROR
    );

    public static final StatusFlow EXPIRE_FLOW = new StatusFlow("Expiration",
            new ChargeStatus[]{
                    CREATED,
                    ENTERING_CARD_DETAILS,
                    AUTHORISATION_SUCCESS
            },
            EXPIRE_CANCEL_READY,
            EXPIRED,
            EXPIRE_CANCEL_FAILED
    );

    private final String name;
    private final ChargeStatus[] terminatableStatuses;
    private final ChargeStatus lockState;
    private final ChargeStatus successTerminalState;
    private final ChargeStatus failureTerminalState;

    private StatusFlow(String name, ChargeStatus[] terminatableStatuses, ChargeStatus lockState, ChargeStatus successTerminalState, ChargeStatus failureTerminalState) {
        this.name = name;
        this.terminatableStatuses = terminatableStatuses;
        this.lockState = lockState;
        this.successTerminalState = successTerminalState;
        this.failureTerminalState = failureTerminalState;
    }

    public ChargeStatus[] getTerminatableStatuses() {
        return terminatableStatuses;
    }

    public String getName() {
        return name;
    }

    public ChargeStatus getLockState() {
        return lockState;
    }

    public ChargeStatus getSuccessTerminalState() {
        return successTerminalState;
    }

    public ChargeStatus getFailureTerminalState() {
        return failureTerminalState;
    }
}
