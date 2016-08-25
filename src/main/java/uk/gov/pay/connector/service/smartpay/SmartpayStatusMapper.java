package uk.gov.pay.connector.service.smartpay;

import org.apache.commons.lang3.tuple.Pair;
import uk.gov.pay.connector.service.BaseStatusMapper;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class SmartpayStatusMapper {

        private static final BaseStatusMapper<Pair<String, Boolean>> STATUS_MAPPER =
                BaseStatusMapper
                        .<Pair<String, Boolean>>builder()
                        .ignore(Pair.of("AUTHORISATION", true))
                        .ignore(Pair.of("AUTHORISATION", false))
                        .map(Pair.of("CAPTURE", true), CAPTURED)
                        .ignore(Pair.of("CAPTURE", false))
                        .ignore(Pair.of("CANCELLATION", true))
                        .ignore(Pair.of("CANCELLATION", false))
                        .ignore(Pair.of("REFUND", true))
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
