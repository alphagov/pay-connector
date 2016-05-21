package uk.gov.pay.connector.model.domain;

import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

public class StateTransitionsTestBase {

    protected final ChargeStatus state;
    protected final List<ChargeStatus> validTransitions;

    public StateTransitionsTestBase(ChargeStatus state, List<ChargeStatus> validTransitions) {
        this.state = state;
        this.validTransitions = validTransitions;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> setParameters() {
        Collection<Object[]> params = new ArrayList<>();

        params.add(new Object[]{CREATED, of(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, EXPIRED)});
        params.add(new Object[]{ENTERING_CARD_DETAILS, of(ENTERING_CARD_DETAILS, AUTHORISATION_READY, EXPIRED, USER_CANCELLED, SYSTEM_CANCELLED)});
        params.add(new Object[]{AUTHORISATION_READY, of(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED, AUTHORISATION_ERROR)});
        params.add(new Object[]{AUTHORISATION_SUCCESS, of(CAPTURE_READY, SYSTEM_CANCEL_READY, USER_CANCEL_READY, EXPIRE_CANCEL_READY)});
        params.add(new Object[]{CAPTURE_READY, of(CAPTURE_SUBMITTED, CAPTURE_ERROR)});
        params.add(new Object[]{EXPIRE_CANCEL_READY, of(EXPIRE_CANCEL_FAILED, EXPIRED)});
        params.add(new Object[]{SYSTEM_CANCEL_READY, of(SYSTEM_CANCEL_ERROR, SYSTEM_CANCELLED)});
        params.add(new Object[]{USER_CANCEL_READY, of(USER_CANCEL_ERROR, USER_CANCELLED)});
        return params;
    }

}
