package uk.gov.pay.connector.gateway.sandbox;

import uk.gov.pay.connector.gateway.StatusMapper;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;

public class SandboxStatusMapper {

    private static final StatusMapper<String> STATUS_MAPPER =
            StatusMapper
                    .<String>builder()
                    .ignore("AUTHORISED")
                    .map("CAPTURED", CAPTURED)
                    .map("REFUNDED", REFUNDED)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
