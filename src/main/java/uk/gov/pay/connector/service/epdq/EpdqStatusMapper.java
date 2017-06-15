package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.ENTERING_CARD_DETAILS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.EXPIRE_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.SYSTEM_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_SUBMITTED;
import static uk.gov.pay.connector.service.StatusFlow.EXPIRE_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.SYSTEM_CANCELLATION_FLOW;
import static uk.gov.pay.connector.service.StatusFlow.USER_CANCELLATION_FLOW;

public class EpdqStatusMapper {

    private static final BaseStatusMapper<String> STATUS_MAPPER =
            BaseStatusMapper
                    .<String>builder()
                    .map("2", AUTHORISATION_REJECTED)
                    .map("5", AUTHORISATION_SUCCESS)
                    .map("6", USER_CANCEL_SUBMITTED, USER_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map("6", SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map("6", EXPIRE_CANCEL_SUBMITTED, EXPIRE_FLOW.getSuccessTerminalState())
                    .map("6", CREATED, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map("6", ENTERING_CARD_DETAILS, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map("9", CAPTURED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
