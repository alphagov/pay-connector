package uk.gov.pay.connector.model.spike;


import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_3DS_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_ABORTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_CANCELLED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_ERROR;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_SUBMITTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_TIMEOUT;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.AUTHORISATION_UNEXPECTED_ERROR;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_APPROVED_RETRY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CAPTURE_SUBMITTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.CREATED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.EXPIRED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.EXPIRE_CANCEL_FAILED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.EXPIRE_CANCEL_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.SYSTEM_CANCELLED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.SYSTEM_CANCEL_ERROR;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.SYSTEM_CANCEL_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.USER_CANCEL_ERROR;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.USER_CANCEL_READY;
import static uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus.USER_CANCEL_SUBMITTED;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import uk.gov.pay.connector.command.StateTransitionGraphVizRenderer;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;

public final class DefaultStateTransitionsNew extends StateTransitionsNew {

    private static final ThreadLocal<Map<TransactionStatus, List<TransactionStatus>>> TRANSITION_TABLE = ThreadLocal
        .withInitial(() -> {
            return ImmutableMap.<TransactionStatus, List<TransactionStatus>>builder()

                .put(CREATED, validTransitions(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, EXPIRED))
                .put(ENTERING_CARD_DETAILS,
                    validTransitions(AUTHORISATION_READY, AUTHORISATION_ABORTED, EXPIRED,
                        USER_CANCELLED, SYSTEM_CANCELLED))
                .put(AUTHORISATION_READY,
                    validTransitions(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED,
                        AUTHORISATION_ERROR,
                        AUTHORISATION_TIMEOUT,
                        AUTHORISATION_UNEXPECTED_ERROR, AUTHORISATION_3DS_REQUIRED,
                        AUTHORISATION_CANCELLED, AUTHORISATION_SUBMITTED))
                .put(AUTHORISATION_SUBMITTED,
                    validTransitions(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED,
                        AUTHORISATION_ERROR,
                        AUTHORISATION_3DS_REQUIRED))
                .put(AUTHORISATION_3DS_REQUIRED,
                    validTransitions(AUTHORISATION_3DS_READY, USER_CANCELLED, EXPIRED))
                .put(AUTHORISATION_3DS_READY,
                    validTransitions(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED,
                        AUTHORISATION_ERROR,
                        AUTHORISATION_CANCELLED))
                .put(AUTHORISATION_SUCCESS,
                    validTransitions(CAPTURE_APPROVED, CAPTURE_READY, SYSTEM_CANCEL_READY,
                        USER_CANCEL_READY, EXPIRE_CANCEL_READY))
                .put(CAPTURE_APPROVED, validTransitions(CAPTURE_READY, CAPTURE_ERROR))
                .put(CAPTURE_APPROVED_RETRY,
                    validTransitions(CAPTURE_READY, CAPTURE_ERROR, CAPTURED))
                .put(CAPTURE_READY,
                    validTransitions(CAPTURE_SUBMITTED, CAPTURE_ERROR, CAPTURE_APPROVED_RETRY))
                .put(CAPTURE_SUBMITTED,
                    validTransitions(CAPTURED)) // can this ever be a capture error?
                .put(EXPIRE_CANCEL_READY,
                    validTransitions(EXPIRE_CANCEL_SUBMITTED, EXPIRE_CANCEL_FAILED, EXPIRED))
                .put(EXPIRE_CANCEL_SUBMITTED, validTransitions(EXPIRE_CANCEL_FAILED, EXPIRED))
                .put(SYSTEM_CANCEL_READY,
                    validTransitions(SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCEL_ERROR,
                        SYSTEM_CANCELLED))
                .put(SYSTEM_CANCEL_SUBMITTED,
                    validTransitions(SYSTEM_CANCEL_ERROR, SYSTEM_CANCELLED))
                .put(USER_CANCEL_READY,
                    validTransitions(USER_CANCEL_SUBMITTED, USER_CANCEL_ERROR, USER_CANCELLED))
                .put(USER_CANCEL_SUBMITTED, validTransitions(USER_CANCEL_ERROR, USER_CANCELLED))
                .build();
        });

    DefaultStateTransitionsNew() {
        super(TRANSITION_TABLE.get());
    }

//    public static StateTransitionGraphVizRenderer dumpGraphViz() {
//        return new StateTransitionGraphVizRenderer(TRANSITION_TABLE);
//    }

}
