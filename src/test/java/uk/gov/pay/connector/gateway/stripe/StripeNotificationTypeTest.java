package uk.gov.pay.connector.gateway.stripe;

import org.junit.Test;
import uk.gov.pay.connector.events.model.payout.PayoutCreated;
import uk.gov.pay.connector.events.model.payout.PayoutFailed;
import uk.gov.pay.connector.events.model.payout.PayoutPaid;
import uk.gov.pay.connector.events.model.payout.PayoutUpdated;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_CREATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_FAILED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_PAID;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.PAYOUT_UPDATED;
import static uk.gov.pay.connector.gateway.stripe.StripeNotificationType.UNKNOWN;

public class StripeNotificationTypeTest {

    @Test
    public void shouldHaveCorrectEventClassAssignedToPayoutTypes() {
        assertThat(PAYOUT_CREATED.getEventClass().get(), is(PayoutCreated.class));
        assertThat(PAYOUT_FAILED.getEventClass().get(), is(PayoutFailed.class));
        assertThat(PAYOUT_PAID.getEventClass().get(), is(PayoutPaid.class));
        assertThat(PAYOUT_UPDATED.getEventClass().get(), is(PayoutUpdated.class));
        assertThat(UNKNOWN.getEventClass().isPresent(), is(false));
    }
}
