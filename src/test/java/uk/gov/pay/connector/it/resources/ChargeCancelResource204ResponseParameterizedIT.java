package uk.gov.pay.connector.it.resources;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.it.base.NewChargingITestBase;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

@RunWith(Parameterized.class)
public class ChargeCancelResource204ResponseParameterizedIT extends NewChargingITestBase {

    @Parameterized.Parameter()
    public ChargeStatus status;

    public ChargeCancelResource204ResponseParameterizedIT() {
        super("worldpay");
    }

    @Parameterized.Parameters()
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                { ChargeStatus.CREATED }, { ChargeStatus.ENTERING_CARD_DETAILS }
        });
    }

    @Test
    public void shouldRespond204WithNoLockingEvent_IfCancelledBeforeAuth() {
        String chargeId = addCharge(status, "ref", Instant.now().minus(1, HOURS), "irrelevant");
        cancelChargeAndCheckApiStatus(chargeId, ChargeStatus.SYSTEM_CANCELLED, 204);

        List<String> events = databaseTestHelper.getInternalEvents(chargeId);
        assertThat(events.size(), is(2));
        assertThat(events, hasItems(status.getValue(), ChargeStatus.SYSTEM_CANCELLED.getValue()));
    }
}
