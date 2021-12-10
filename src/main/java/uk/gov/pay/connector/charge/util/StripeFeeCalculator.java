package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.gateway.model.request.CaptureGatewayRequest;

import java.util.ArrayList;
import java.util.List;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.FeeType.RADAR;
import static uk.gov.pay.connector.charge.model.domain.FeeType.THREE_D_S;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;

public class StripeFeeCalculator {

    private StripeFeeCalculator() {
    }

    public static Long getTotalAmountForConnectFee(Long stripeFee, CaptureGatewayRequest request, Double feePercentage) {
        Double platformFee = getPlatformFee(feePercentage, request.getAmount());
        return stripeFee + platformFee.longValue();
    }

    public static Long getTotalAmountForV2(Long stripeFee, CaptureGatewayRequest request, Double feePercentage,
                                           int radarFeeInPence, int threeDsFeeInPence) {
        Double platformFee = getPlatformFee(feePercentage, request.getAmount());
        platformFee += (stripeFee + radarFeeInPence);
        if (is3dsUsed(request)) {
            platformFee += threeDsFeeInPence;
        }
        return platformFee.longValue();
    }

    public static List<Fee> getFeeList(Long stripeFee, CaptureGatewayRequest request, Double feePercentage,
                                       int radarFeeInPence, int threeDsFeeInPence) {
        List<Fee> feeList = new ArrayList<>();
        feeList.add(Fee.of(TRANSACTION, getTotalAmountForConnectFee(stripeFee, request, feePercentage)));
        feeList.add(Fee.of(RADAR, Long.valueOf(radarFeeInPence)));
        if (is3dsUsed(request)) {
            feeList.add(Fee.of(THREE_D_S, Long.valueOf(threeDsFeeInPence)));
        }
        return feeList;
    }

    private static Double getPlatformFee(Double feePercentage, Long grossChargeAmount) {
        return Math.ceil((feePercentage / 100) * grossChargeAmount);
    }

    private static boolean is3dsUsed(CaptureGatewayRequest request) {
        return request.getEvents()
                .stream()
                .anyMatch(chargeEventEntity -> chargeEventEntity.getStatus().equals(AUTHORISATION_3DS_REQUIRED));
    }
}
