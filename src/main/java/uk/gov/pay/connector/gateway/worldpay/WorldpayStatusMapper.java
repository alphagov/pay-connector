package uk.gov.pay.connector.gateway.worldpay;

import uk.gov.pay.connector.gateway.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class WorldpayStatusMapper {

    private static final StatusMapper<String> STATUS_MAPPER =
            StatusMapper
                    .<String>builder()
                    .ignore("SENT_FOR_AUTHORISATION")
                    .ignore("AUTHORISED")
                    .ignore("CANCELLED")
                    .ignore("EXPIRED")
                    .map("CAPTURED", CAPTURED)
                    .ignore("REFUSED")
                    .ignore("REFUSED_BY_BANK")
                    .ignore("SETTLED_BY_MERCHANT")
                    .ignore("SENT_FOR_REFUND")
                    .map("REFUNDED", REFUNDED)
                    .map("REFUNDED_BY_MERCHANT", REFUNDED)
                    .map("REFUND_FAILED", REFUND_ERROR)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
