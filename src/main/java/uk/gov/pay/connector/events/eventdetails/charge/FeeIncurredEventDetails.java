package uk.gov.pay.connector.events.eventdetails.charge;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.eventdetails.EventDetails;
import uk.gov.pay.connector.fee.model.Fee;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FeeIncurredEventDetails extends EventDetails {
    
    private final Long fee;
    private final Long netAmount;
    private final List<Fee> feeBreakdown;

    private FeeIncurredEventDetails(Long fee, Long netAmount, List<Fee> feeBreakdown) {
        this.fee = fee;
        this.netAmount = netAmount;
        this.feeBreakdown = feeBreakdown;
    }
    
    public static FeeIncurredEventDetails from(ChargeEntity charge) {
        List<Fee> listOfFees = charge.getFees()
                .stream()
                .map(Fee::from)
                .collect(Collectors.toList());
        
        return new FeeIncurredEventDetails(charge.getFeeAmount().orElse(null),
                charge.getNetAmount().orElse(null),
                listOfFees
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeeIncurredEventDetails that = (FeeIncurredEventDetails) o;
        return Objects.equals(fee, that.fee) &&
                Objects.equals(netAmount, that.netAmount) &&
                feeBreakdown.equals(feeBreakdown);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fee, netAmount, feeBreakdown);
    }
}
