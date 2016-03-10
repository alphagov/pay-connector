package uk.gov.pay.connector.service;

import com.google.common.collect.ImmutableList;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.List;

import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class ChargeStatusBlacklist {

    private static final List<ChargeStatus> blackList = ImmutableList.<ChargeStatus>builder()
            .add(AUTHORISATION_SUBMITTED)
            .add(AUTHORISATION_READY)
            .add(AUTHORISATION_SUCCESS)
            .add(AUTHORISATION_REJECTED)
            .build();

    public boolean has(ChargeStatus chargeStatus) {
        return blackList.stream()
                .filter(cs -> cs == chargeStatus)
                .findAny()
                .isPresent();
    }
}
