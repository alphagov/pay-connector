package uk.gov.pay.connector.service.epdq;

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

    public static final String EPDQ_AUTHORISATION_REFUSED = "2";
    public static final String EPDQ_AUTHORISED = "5";
    public static final String EPDQ_AUTHORISED_CANCELLED = "6";
    public static final String EPDQ_PAYMENT_REQUESTED = "9";

    private static final StatusMapper<String> STATUS_MAPPER =
            StatusMapper
                    .<String>builder()
                    .map(EPDQ_AUTHORISATION_REFUSED, AUTHORISATION_REJECTED)
                    .map(EPDQ_AUTHORISED, AUTHORISATION_SUCCESS)
                    .map(EPDQ_AUTHORISED_CANCELLED, USER_CANCEL_SUBMITTED, USER_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map(EPDQ_AUTHORISED_CANCELLED, SYSTEM_CANCEL_SUBMITTED, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map(EPDQ_AUTHORISED_CANCELLED, EXPIRE_CANCEL_SUBMITTED, EXPIRE_FLOW.getSuccessTerminalState())
                    .map(EPDQ_AUTHORISED_CANCELLED, CREATED, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map(EPDQ_AUTHORISED_CANCELLED, ENTERING_CARD_DETAILS, SYSTEM_CANCELLATION_FLOW.getSuccessTerminalState())
                    .map(EPDQ_PAYMENT_REQUESTED, CAPTURED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
