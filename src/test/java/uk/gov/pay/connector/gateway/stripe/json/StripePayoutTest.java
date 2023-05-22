package uk.gov.pay.connector.gateway.stripe.json;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

class StripePayoutTest {

    @Test
    void shouldReturnCreatedAsZonedDateTime() {
        StripePayout payout = new StripePayout("po_123", 1213L, null, 1589395533L,
                "pending", "card", null);

        assertThat(payout.getCreated().toString(), is("2020-05-13T18:45:33Z"));
    }

    @Test
    void shouldReturnArrivalDateAsZonedDateTime() {
        StripePayout payout = new StripePayout("po_123", 1213L, 1589395533L,
                null, "pending", "card", null);

        assertThat(payout.getArrivalDate().toString(), is("2020-05-13T18:45:33Z"));
    }
}

