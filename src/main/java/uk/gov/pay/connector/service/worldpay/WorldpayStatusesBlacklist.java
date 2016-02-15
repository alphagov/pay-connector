package uk.gov.pay.connector.service.worldpay;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class WorldpayStatusesBlacklist {

    private static final List<ChargeStatus> blackList = ImmutableList.<ChargeStatus>builder()
            .add(AUTHORISATION_SUBMITTED)
            .add(AUTHORISATION_SUCCESS)
            .add(AUTHORISATION_REJECTED)
            .build();

    public static boolean has(ChargeStatus chargeStatus) {
        return blackList.stream()
                .filter(cs -> cs == chargeStatus)
                .findAny()
                .isPresent();
    }
}
