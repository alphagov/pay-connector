package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;

public class StripeFeeCalculator {

    private StripeFeeCalculator() {
    }

    public static Optional<Long> getTotalAmountFor(Long stripeFee, CaptureGatewayRequest request, Double feePercentage) {
        Double platformFee = getPlatformFee(feePercentage, request.getAmount());
        return Optional.of(stripeFee + platformFee.longValue());
    }

    public static Optional<Long> getTotalAmountForV2(Long stripeFee, CaptureGatewayRequest request, Double feePercentage,
                                                     int radarFeeInPence, int threeDsFeeInPence) {
        Double platformFee = getPlatformFee(feePercentage, request.getAmount());
        platformFee += (stripeFee + radarFeeInPence);
        boolean is3dsUsed = request.getEvents()
                .stream()
                .anyMatch(chargeEventEntity -> chargeEventEntity.getStatus().equals(AUTHORISATION_3DS_REQUIRED));
        if (is3dsUsed) {
            platformFee += threeDsFeeInPence;
        }
        return Optional.of(platformFee.longValue());
    }

    private static Double getPlatformFee(Double feePercentage, Long grossChargeAmount) {
        return Math.ceil((feePercentage / 100) * grossChargeAmount);
    }
}
