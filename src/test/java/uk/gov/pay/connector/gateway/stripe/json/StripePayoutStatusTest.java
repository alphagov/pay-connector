package uk.gov.pay.connector.gateway.stripe.json;

import org.junit.Test;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.stripe.json.StripePayoutStatus.CANCELLED;
import static uk.gov.pay.connector.gateway.stripe.json.StripePayoutStatus.FAILED;
import static uk.gov.pay.connector.gateway.stripe.json.StripePayoutStatus.IN_TRANSIT;
import static uk.gov.pay.connector.gateway.stripe.json.StripePayoutStatus.PAID;
import static uk.gov.pay.connector.gateway.stripe.json.StripePayoutStatus.PENDING;

public class StripePayoutStatusTest {

    @Test
    public void shouldHaveCorrectEventClassAndTerminalStatusAssignedToPayoutStatus() {
        assertThat(PENDING.getEventClass().isPresent(), is(false));
        assertThat(IN_TRANSIT.getEventClass().isPresent(), is(false));
        assertThat(CANCELLED.getEventClass().isPresent(), is(false));

        assertThat(PAID.getEventClass().get(), is(PayoutPaid.class));
        assertThat(PAID.isTerminal(), is(true));
        assertThat(FAILED.getEventClass().get(), is(PayoutFailed.class));
        assertThat(FAILED.isTerminal(), is(true));
    }

    @Test
    public void fromStringShouldReturnCorrectStripePayoutStatus() {
        StripePayoutStatus stripePayoutStatus = StripePayoutStatus.fromString("paid");

        assertThat(stripePayoutStatus, is(PAID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromStringShouldThrowExceptionForInvalidPayoutStatus() {
        StripePayoutStatus.fromString("unknown");
    }
}
