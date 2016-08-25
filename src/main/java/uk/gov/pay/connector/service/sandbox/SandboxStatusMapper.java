package uk.gov.pay.connector.service.sandbox;

import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;

public class SandboxStatusMapper {

    private static final BaseStatusMapper<String> STATUS_MAPPER =
            BaseStatusMapper
                    .<String>builder()
                    .ignore("AUTHORISED")
                    .map("CAPTURED", CAPTURED)
                    .map("REFUNDED", REFUNDED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
