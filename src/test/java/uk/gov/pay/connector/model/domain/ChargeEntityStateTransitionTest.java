package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.List;

import static java.lang.String.format;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ChargeEntityStateTransitionTest extends StateTransitionsTestBase {

    public ChargeEntityStateTransitionTest(ChargeStatus state, List<ChargeStatus> validTransitions) {
        super(state, validTransitions);
    }


    @Test
    public void shouldAllowCorrectStateTransitions() throws Exception {

        try {
            validTransitions.forEach(targetState -> {
                ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(state).build();
                chargeCreated.setStatus(targetState);
            });
        } catch (InvalidStateTransitionException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void shouldErrorInvalidStateTransitions() throws Exception {
        try {
            EnumSet.allOf(ChargeStatus.class).stream()
                    .filter(s -> !validTransitions.contains(s))
                    .forEach(targetState -> {
                        ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(state).build();
                        chargeCreated.setStatus(targetState);
                        fail(format("Charge state transition [%s] -> [%s] should not have been allowed", state, targetState));
                    });

        } catch (InvalidStateTransitionException e) {
            assertTrue(true);
        }
    }

}
