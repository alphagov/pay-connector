package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayStatusesMapper {

    private static final Logger logger = LoggerFactory.getLogger(WorldpayStatusesMapper.class);

    private static final Map<String, ChargeStatus> worldpayStatuses = ImmutableMap.<String, ChargeStatus>builder()
                .put("SENT_FOR_AUTHORISATION", AUTHORISATION_SUBMITTED)
                .put("AUTHORISED", AUTHORISATION_SUCCESS)
                .put("CANCELLED", SYSTEM_CANCELLED)
                .put("CAPTURED", CAPTURED)
                .put("REFUSED", AUTHORISATION_REJECTED)
                .put("REFUSED_BY_BANK", AUTHORISATION_REJECTED).build();

    public static Optional<ChargeStatus> mapToChargeStatus(String worldpayStatus) {
        Optional<ChargeStatus> chargeStatus = ofNullable(worldpayStatuses.get(worldpayStatus));
        if (!chargeStatus.isPresent()) {
            logger.error(format("Could not map worldpay status %s to our internal status.", worldpayStatus));
        }
        return chargeStatus;

    }


}
