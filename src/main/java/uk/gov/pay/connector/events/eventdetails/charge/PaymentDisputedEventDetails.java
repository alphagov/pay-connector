package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;

public class PaymentDisputedEventDetails extends EventDetails {
    private static final boolean disputed = true;
    
    public boolean isDisputed() {
        return disputed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash(disputed);
    }
}
