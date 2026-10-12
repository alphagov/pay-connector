package uk.gov.pay.connector.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.charge.model.domain.FeeSubType;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.fee.model.Fee;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.pay.connector.charge.util.AdyenFeeCalculator.hasAllExpectedFeeRecordsForAdyen;
import static uk.gov.pay.connector.common.model.api.ExternalChargeState.EXTERNAL_SUCCESS;
import static uk.gov.pay.connector.gateway.PaymentGatewayName.ADYEN;

public class ChargeFeeCalculator {
    
    private  ChargeFeeCalculator() {
    }
    
    public static Optional<Long> calculateFeeAmount(ChargeEntity charge) {
        List<FeeEntity> fees = charge.getFees();

        if (fees.isEmpty() || !hasRequiredFeesForAdyen(charge)) {
            return Optional.empty();
        }


        return Optional.of(fees.stream()
                .collect(Collectors.toMap(
                        fee -> new FeeKey(fee.getFeeType(), fee.getFeeSubType()),
                        FeeEntity::getAmountCollected,
                        (existing, _) -> existing
                ))
                .values()
                .stream()
                .mapToLong(Long::longValue)
                .sum());
    }

    public static Optional<Long> calculateNetAmount(ChargeEntity charge) {
        return calculateFeeAmount(charge).map(fee -> {
            if (charge.getChargeStatus().toExternal() == EXTERNAL_SUCCESS) {
                return charge.getAmount() + charge.getCorporateSurcharge().orElse(0L) - fee;
            }
            return -fee;
        });
    }

    private static boolean hasRequiredFeesForAdyen(ChargeEntity charge) {
        if (ADYEN.getName().equals(charge.getPaymentProvider()) && charge.getChargeStatus().toExternal() == EXTERNAL_SUCCESS) {
            List<Fee> mappedFees = charge.getFees().stream().map(Fee::from).toList();
            return hasAllExpectedFeeRecordsForAdyen(mappedFees);
        }
        return true;
    }
    
    private record FeeKey (FeeType feeType, FeeSubType feeSubType) {}
}
