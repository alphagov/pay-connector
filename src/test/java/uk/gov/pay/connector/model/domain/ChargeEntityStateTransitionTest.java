package uk.gov.pay.connector.model.domain;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.exception.InvalidStateTransitionException;

import java.util.EnumSet;
import java.util.List;

import static java.lang.String.format;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(Parameterized.class)
public class ChargeEntityStateTransitionTest extends StateTransitionsTestBase {

    public ChargeEntityStateTransitionTest(ChargeStatus state, List<ChargeStatus> validTransitions) {
        super(state, validTransitions);
    }


    @Test
    public void shouldAllowCorrectStateTransitions() throws Exception {
        validTransitions.forEach(targetState -> {
            ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(state).build();
            try {
                chargeCreated.setStatus(targetState);
                assertThat(chargeCreated.getStatus(), is(targetState.getValue()));
            } catch (InvalidStateTransitionException e) {
                fail(e.getMessage());
            }
        });

    }

    @Test
    public void shouldErrorInvalidStateTransitions() throws Exception {

        EnumSet.allOf(ChargeStatus.class).stream()
                .filter(s -> !validTransitions.contains(s))
                .forEach(targetState -> {
                    ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withStatus(state).build();
                    try {
                        chargeCreated.setStatus(targetState);
                        fail(format("Charge state transition [%s] -> [%s] should not have been allowed", state, targetState));
                    } catch (InvalidStateTransitionException e) {
                        assertThat(e.getMessage(), is(format("Charge state transition [%s] -> [%s] not allowed", state, targetState)));
                    }
                });


    }

}
