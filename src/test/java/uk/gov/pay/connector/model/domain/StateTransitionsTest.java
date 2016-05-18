package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.EnumSet;
import java.util.List;

import static java.lang.String.format;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class StateTransitionsTest extends StateTransitionsTestBase {

    public StateTransitionsTest(ChargeStatus state, List<ChargeStatus> validTransitions) {
        super(state, validTransitions);
    }

    @Test
    public void shouldValidateCorrectTransitions() throws Exception {

        validTransitions.forEach(targetState ->
                assertTrue(format("Charge transition [%s] -> [%s] assumes valid, but not!", state, targetState),
                        StateTransitions.transitionTo(state, targetState))
        );

    }

    @Test
    public void shouldInvalidateAllInvalidStateTransitions() throws Exception {

        EnumSet.allOf(ChargeStatus.class).stream()
                .filter(s -> !validTransitions.contains(s))
                .forEach(targetState ->
                        assertFalse(format("ChargeStatus transition [%s] -> [%s] is not a valid", state, targetState),
                                StateTransitions.transitionTo(state, targetState))
                );
    }
}

