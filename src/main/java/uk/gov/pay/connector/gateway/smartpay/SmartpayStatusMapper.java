package uk.gov.pay.connector.gateway.smartpay;

import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.gateway.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_ERROR;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.model.domain.RefundStatus.REFUND_ERROR;

public class SmartpayStatusMapper {

        private static final StatusMapper<Pair<String, Boolean>> STATUS_MAPPER =
                StatusMapper
                        .<Pair<String, Boolean>>builder()
                        .ignore(Pair.of("AUTHORISATION", true))
                        .ignore(Pair.of("AUTHORISATION", false))
                        .map(Pair.of("CAPTURE", true), CAPTURED)
                        .map(Pair.of("CAPTURE", false), CAPTURE_ERROR)
                        .ignore(Pair.of("CANCELLATION", true))
                        .ignore(Pair.of("CANCELLATION", false))
                        .map(Pair.of("REFUND", true), REFUNDED)
                        .map(Pair.of("REFUND", false), REFUND_ERROR)
                        .map(Pair.of("REFUND_FAILED", true), REFUND_ERROR)    // TODO:: Check with Smartpay what is a valid business use case
                        .map(Pair.of("REFUND_FAILED", false), REFUND_ERROR)
                        .ignore(Pair.of("REFUND", false))
                        .ignore(Pair.of("REQUEST_FOR_INFORMATION", true))
                        .ignore(Pair.of("REQUEST_FOR_INFORMATION", false))
                        .ignore(Pair.of("NOTIFICATION_OF_CHARGEBACK", true))
                        .ignore(Pair.of("NOTIFICATION_OF_CHARGEBACK", false))
                        .ignore(Pair.of("CHARGEBACK", true))
                        .ignore(Pair.of("CHARGEBACK", false))
                        .ignore(Pair.of("CHARGEBACK_REVERSED", true))
                        .ignore(Pair.of("CHARGEBACK_REVERSED", false))
                        .ignore(Pair.of("REPORT_AVAILABLE", true))
                        .ignore(Pair.of("REPORT_AVAILABLE", false))
                        .build();

        public static StatusMapper<Pair<String, Boolean>> get() {
                return STATUS_MAPPER;
        }
}
