package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.CancelStatusResolver;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class EpdqStatusMapper {

    public static final String EPDQ_AUTHORISATION_REFUSED = "2";
    public static final String EPDQ_AUTHORISED = "5";
    public static final String EPDQ_AUTHORISED_CANCELLED = "6";
    public static final String EPDQ_PAYMENT_REQUESTED = "9";

    private static final BaseStatusMapper<String> STATUS_MAPPER =
            BaseStatusMapper
                    .<String>builder()
                    .map(EPDQ_AUTHORISATION_REFUSED, AUTHORISATION_REJECTED)
                    .map(EPDQ_AUTHORISED, AUTHORISATION_SUCCESS)
                    .mapDeferred(EPDQ_AUTHORISED_CANCELLED, new CancelStatusResolver())
                    .map(EPDQ_PAYMENT_REQUESTED, CAPTURED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
