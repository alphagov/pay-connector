package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

import java.util.Objects;
import java.util.Optional;

public class Gateway3dsExemptionResultObtainedEventDetails extends EventDetails {

    private final String exemption3ds;

    public Gateway3dsExemptionResultObtainedEventDetails(String exemption3ds) {
        this.exemption3ds = exemption3ds;
    }

    public static Gateway3dsExemptionResultObtainedEventDetails from(ChargeEntity charge) {
        String exemption3ds = Optional.ofNullable(charge.getExemption3ds()).map(Enum::toString).orElse(null);
        return new Gateway3dsExemptionResultObtainedEventDetails(exemption3ds);
    }

    public String getExemption3ds() {
        return exemption3ds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Gateway3dsExemptionResultObtainedEventDetails that = (Gateway3dsExemptionResultObtainedEventDetails) o;
        return Objects.equals(exemption3ds, that.exemption3ds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(exemption3ds);
    }
}
