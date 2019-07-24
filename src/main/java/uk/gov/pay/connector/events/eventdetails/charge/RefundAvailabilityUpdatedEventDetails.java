package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.util.RefundCalculator;
import uk.gov.pay.connector.common.model.api.ExternalChargeRefundAvailability;
import uk.gov.pay.connector.events.eventdetails.EventDetails;

public class RefundAvailabilityUpdatedEventDetails extends EventDetails {
    private Long amountAvailable;
    private Long amountRefunded;
    private String availability;

    private RefundAvailabilityUpdatedEventDetails(Long amountAvailable, Long amountRefunded, String availability) {
        this.amountAvailable = amountAvailable;
        this.amountRefunded = amountRefunded;
        this.availability = availability;
    }
    

    public static RefundAvailabilityUpdatedEventDetails from(ChargeEntity charge, ExternalChargeRefundAvailability availability) {
        return new RefundAvailabilityUpdatedEventDetails(
                RefundCalculator.getTotalAmountAvailableToBeRefunded(charge),
                RefundCalculator.getRefundedAmount(charge),
                availability.getStatus()
        );
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
}
