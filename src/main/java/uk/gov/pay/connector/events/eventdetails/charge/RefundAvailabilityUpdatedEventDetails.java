package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.refund.model.domain.Refund;

import java.util.List;
import java.util.Objects;

public class RefundAvailabilityUpdatedEventDetails extends EventDetails {
    private Long amountAvailable;
    private Long amountRefunded;
    private String availability;

    private RefundAvailabilityUpdatedEventDetails(Long amountAvailable, Long amountRefunded, String availability) {
        this.amountAvailable = amountAvailable;
        this.amountRefunded = amountRefunded;
        this.availability = availability;
    }
    

    public static RefundAvailabilityUpdatedEventDetails from(Charge charge, List<Refund> refundList, ExternalChargeRefundAvailability availability) {
        return new RefundAvailabilityUpdatedEventDetails(
                RefundCalculator.getTotalAmountAvailableToBeRefunded(charge, refundList),
                RefundCalculator.getRefundedAmount(refundList),
                availability.getStatus()
        );
    }
    
    public static RefundAvailabilityUpdatedEventDetails from(ExternalChargeRefundAvailability availability) {
        return new RefundAvailabilityUpdatedEventDetails(null, null, availability.getStatus());
    }

    public Long getRefundAmountAvailable() {
        return amountAvailable;
    }

    public Long getRefundAmountRefunded() {
        return amountRefunded;
    }

    public String getRefundStatus() {
        return availability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefundAvailabilityUpdatedEventDetails that = (RefundAvailabilityUpdatedEventDetails) o;
        return Objects.equals(amountAvailable, that.amountAvailable) && Objects.equals(amountRefunded, that.amountRefunded) && Objects.equals(availability, that.availability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountAvailable, amountRefunded, availability);
    }
}
