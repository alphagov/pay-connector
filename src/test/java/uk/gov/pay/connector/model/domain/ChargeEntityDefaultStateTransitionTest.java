package uk.gov.pay.connector.model.domain;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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
public class ChargeEntityDefaultStateTransitionTest extends DefaultStateTransitionsTestBase {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    public ChargeEntityDefaultStateTransitionTest(ChargeStatus state, List<ChargeStatus> validTransitions) {
        super(state, validTransitions);
    }

    @Test
    public void shouldAllowCorrectStateTransitionsForWorldpay() throws Exception {
        validTransitions.forEach(targetState -> {
            GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
            gatewayAccountEntity.setGatewayName("worldpay");
            ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(state).build();
            try {
                chargeCreated.setStatus(targetState);
                assertThat(chargeCreated.getStatus(), is(targetState.getValue()));
            } catch (InvalidStateTransitionException e) {
                fail(e.getMessage());
            }
        });

    }

    @Test
    public void shouldErrorInvalidStateTransitionsForWorldpay() throws Exception {

        EnumSet.allOf(ChargeStatus.class).stream()
                .filter(s -> !validTransitions.contains(s))
                .forEach(targetState -> {
                    GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
                    gatewayAccountEntity.setGatewayName("worldpay");
                    ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(state).build();

                    thrown.expect(InvalidStateTransitionException.class);
                    thrown.expectMessage(is(format("Charge state transition [%s] -> [%s] not allowed", state, targetState)));

                    chargeCreated.setStatus(targetState);

                    fail(format("Charge state transition [%s] -> [%s] should not have been allowed", state, targetState));
                });
    }

    @Test
    public void shouldAllowCorrectStateTransitionsForSmartpay() throws Exception {
        validTransitions.forEach(targetState -> {
            GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
            gatewayAccountEntity.setGatewayName("smartpay");
            ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(state).build();
            try {
                chargeCreated.setStatus(targetState);
                assertThat(chargeCreated.getStatus(), is(targetState.getValue()));
            } catch (InvalidStateTransitionException e) {
                fail(e.getMessage());
            }
        });

    }

    @Test
    public void shouldErrorInvalidStateTransitionsForSmartpay() throws Exception {

        EnumSet.allOf(ChargeStatus.class).stream()
                .filter(s -> !validTransitions.contains(s))
                .forEach(targetState -> {
                    GatewayAccountEntity gatewayAccountEntity = ChargeEntityFixture.defaultGatewayAccountEntity();
                    gatewayAccountEntity.setGatewayName("smartpay");
                    ChargeEntity chargeCreated = ChargeEntityFixture.aValidChargeEntity().withGatewayAccountEntity(gatewayAccountEntity).withStatus(state).build();

                    thrown.expect(InvalidStateTransitionException.class);
                    thrown.expectMessage(is(format("Charge state transition [%s] -> [%s] not allowed", state, targetState)));

                    chargeCreated.setStatus(targetState);

                    fail(format("Charge state transition [%s] -> [%s] should not have been allowed", state, targetState));
                });
    }
}
