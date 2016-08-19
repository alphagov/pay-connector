package uk.gov.pay.connector.service.worldpay;

import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.service.StatusMapper;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURED;

public class WorldpayStatusMapper {

    private static final StatusMapper<String, ChargeStatus> statusMapper =
            StatusMapper
                    .<String, ChargeStatus>builder()
                    .ignore("AUTHORISED")
                    .ignore("CANCELLED")
                    .map("CAPTURED", CAPTURED)
                    .ignore("REFUSED")
                    .ignore("REFUSED_BY_BANK")
                    .ignore("SENT_FOR_AUTHORISATION")
                    .build();

    public static StatusMapper.Status<ChargeStatus> from(String status) {
        return statusMapper.from(status);
    }


}
