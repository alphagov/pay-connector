package uk.gov.pay.connector.service.epdq;

import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.CancelStatusResolver;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_REJECTED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.AUTHORISATION_SUCCESS;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class EpdqStatusMapper {

    private static final BaseStatusMapper<String> STATUS_MAPPER =
            BaseStatusMapper
                    .<String>builder()
                    .map("2", AUTHORISATION_REJECTED)
                    .map("5", AUTHORISATION_SUCCESS)
                    .mapDeferred("6", new CancelStatusResolver())
                    .map("9", CAPTURED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
