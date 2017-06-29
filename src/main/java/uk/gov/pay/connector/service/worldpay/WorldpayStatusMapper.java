package uk.gov.pay.connector.service.worldpay;

import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class WorldpayStatusMapper {

    private static final StatusMapper<String> STATUS_MAPPER =
            StatusMapper
                    .<String>builder()
                    .ignore("AUTHORISED")
                    .ignore("CANCELLED")
                    .map("CAPTURED", CAPTURED)
                    .ignore("REFUSED")
                    .ignore("REFUSED_BY_BANK")
                    .ignore("SENT_FOR_AUTHORISATION")
                    .map("REFUNDED", REFUNDED)
                    .map("REFUNDED_BY_MERCHANT", REFUNDED)
                    .map("REFUND_FAILED", REFUND_ERROR)
                    .build();

    public static StatusMapper<String> get() {
        return STATUS_MAPPER;
    }
}
