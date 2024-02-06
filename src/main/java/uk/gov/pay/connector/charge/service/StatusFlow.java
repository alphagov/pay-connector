package uk.gov.pay.connector.charge.service;

import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ExpirableChargeStatus;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

public class StatusFlow {

    private static final List<ChargeStatus> TERMINABLE_STATUSES = ExpirableChargeStatus.getValuesAsStream()
            .map(ExpirableChargeStatus::getChargeStatus)
            .collect(Collectors.toList());

    public static final StatusFlow USER_CANCELLATION_FLOW = new StatusFlow("User Cancellation",
            TERMINABLE_STATUSES,
            USER_CANCEL_READY,
            USER_CANCELLED,
            USER_CANCEL_SUBMITTED,
            USER_CANCEL_ERROR
    );

    public static final StatusFlow SYSTEM_CANCELLATION_FLOW = new StatusFlow("System Cancellation",
            TERMINABLE_STATUSES,
            SYSTEM_CANCEL_READY,
            SYSTEM_CANCELLED,
            SYSTEM_CANCEL_SUBMITTED,
            SYSTEM_CANCEL_ERROR
    );
    
    public static final StatusFlow EXPIRE_FLOW = new StatusFlow("Expiration",
            TERMINABLE_STATUSES, 
            EXPIRE_CANCEL_READY,
            EXPIRED,
            EXPIRE_CANCEL_SUBMITTED,
            EXPIRE_CANCEL_FAILED
    );

    private final String name;
    private final List<ChargeStatus> terminatableStatuses;
    private final ChargeStatus lockState;
    private final ChargeStatus successTerminalState;
    private final ChargeStatus submittedState;
    private final ChargeStatus failureTerminalState;

    private StatusFlow(String name, List<ChargeStatus> terminableStatuses, ChargeStatus lockState, ChargeStatus successTerminalState, ChargeStatus submittedState, ChargeStatus failureTerminalState) {
        this.name = name;
        this.terminatableStatuses = terminableStatuses;
        this.lockState = lockState;
        this.successTerminalState = successTerminalState;
        this.submittedState = submittedState;
        this.failureTerminalState = failureTerminalState;
    }

    public List<ChargeStatus> getTerminatableStatuses() {
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

    public ChargeStatus getSubmittedState() {
        return submittedState;
    }

    public ChargeStatus getFailureTerminalState() {
        return failureTerminalState;
    }
    
    public boolean isInProgress(ChargeStatus chargeStatus) {
        return Set.of(lockState, submittedState).contains(chargeStatus);
    }
}
