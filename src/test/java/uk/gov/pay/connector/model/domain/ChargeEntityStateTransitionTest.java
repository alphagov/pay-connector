package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.google.common.collect.ImmutableList.of;
import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.pay.connector.model.domain.ChargeStatus.*;

@RunWith(Parameterized.class)
public class ChargeEntityStateTransitionTest {

    private final ChargeStatus state;
    private final List<ChargeStatus> validTransitions;

    public ChargeEntityStateTransitionTest(ChargeStatus state, List<ChargeStatus> validTransitions) {
        this.state = state;
        this.validTransitions = validTransitions;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> setParameters() {
        Collection<Object[]> params = new ArrayList<>();

        params.add(new Object[]{CREATED, of(ENTERING_CARD_DETAILS, SYSTEM_CANCELLED, EXPIRED)});
        params.add(new Object[]{ENTERING_CARD_DETAILS, of(ENTERING_CARD_DETAILS, AUTHORISATION_READY, EXPIRED, USER_CANCELLED)});
        params.add(new Object[]{AUTHORISATION_READY, of(AUTHORISATION_SUCCESS, AUTHORISATION_REJECTED, AUTHORISATION_ERROR)});
       //AUTHORIZATION_SUBMITTED need to be removed
        params.add(new Object[]{AUTHORISATION_SUCCESS, of(CAPTURE_READY, CANCEL_READY, EXPIRE_CANCEL_PENDING)});
        params.add(new Object[]{CAPTURE_READY, of(CAPTURE_SUBMITTED, CAPTURE_ERROR)});
        params.add(new Object[]{EXPIRE_CANCEL_PENDING, of(EXPIRE_CANCEL_FAILED, EXPIRED)});
        params.add(new Object[]{CANCEL_READY, of(CANCEL_ERROR, SYSTEM_CANCELLED, USER_CANCEL_ERROR, USER_CANCELLED)});

        return params;
    }

    @Test
    public void shouldValidateCorrectTransitionsAndInvalidTransitions() throws Exception {

        validTransitions.forEach(targetState ->
               assertTrue(format("Charge transition [%s] -> [%s] is missing", state, targetState), StateTransitions.transitionTo(state, targetState))
        );

        EnumSet.allOf(ChargeStatus.class).stream()
                .filter(s -> !validTransitions.contains(s))
                .forEach(targetState ->
                   assertFalse(format("ChargeStatus transition [%s] -> [%s] is not a valid", state, targetState), StateTransitions.transitionTo(state, targetState))
                );
    }
}

