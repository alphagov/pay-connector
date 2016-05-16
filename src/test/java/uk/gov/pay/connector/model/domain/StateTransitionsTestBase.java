package uk.gov.pay.connector.model.domain;

import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCELLED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.USER_CANCEL_ERROR;

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
        //AUTHORIZATION_SUBMITTED need to be removed
        params.add(new Object[]{AUTHORISATION_SUCCESS, of(CAPTURE_READY, CANCEL_READY, EXPIRE_CANCEL_PENDING)});
        params.add(new Object[]{CAPTURE_READY, of(CAPTURE_SUBMITTED, CAPTURE_ERROR)});
        //FIXME: remove EXPIRE_CANCEL_PENDING --> SYSTEM_CANCELLED when cancellation fix is done
        params.add(new Object[]{EXPIRE_CANCEL_PENDING, of(EXPIRE_CANCEL_FAILED, SYSTEM_CANCELLED, EXPIRED, CANCEL_READY)});
        params.add(new Object[]{CANCEL_READY, of(CANCEL_ERROR, SYSTEM_CANCELLED, USER_CANCEL_ERROR, USER_CANCELLED)});
        params.add(new Object[]{SYSTEM_CANCELLED, of(EXPIRED)});

        return params;
    }

}
