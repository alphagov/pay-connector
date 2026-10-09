package uk.gov.pay.connector.charge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.charge.model.domain.FeeSubType;
import uk.gov.pay.connector.charge.model.domain.FeeType;
import uk.gov.pay.connector.fee.model.Fee;
import uk.gov.pay.connector.queue.tasks.handlers.adyen.fee.AdyenFeeTransferType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static uk.gov.pay.connector.charge.model.domain.FeeSubType.FIXED;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.INTERCHANGE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.SCHEME_FEE;
import static uk.gov.pay.connector.charge.model.domain.FeeSubType.VARIABLE;
import static uk.gov.pay.connector.charge.model.domain.FeeType.FRAUD_PROTECTION;
import static uk.gov.pay.connector.charge.model.domain.FeeType.GATEWAY;
import static uk.gov.pay.connector.charge.model.domain.FeeType.TRANSACTION;

public class AdyenFeeCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdyenFeeCalculator.class);

    private static final Set<FeeType> REQUIRED_ADYEN_FEE_TYPES = EnumSet.of(GATEWAY, FRAUD_PROTECTION);
    private static final List<Set<FeeSubType>> VALID_ADYEN_TRANSACTION_FEE_SUB_TYPE_COMBINATION = List.of(
            EnumSet.of(FIXED, VARIABLE),
            EnumSet.of(FIXED, INTERCHANGE, SCHEME_FEE),
            // The fixed commission transfer amount is split into a `gateway`, `fraud-protection` (as per the config), and `fixed` transaction fee (for the rest of the amount). 
            // So, we should not see gateway + fraud_protection fees without a required `fixed` transaction fee.
            EnumSet.of(VARIABLE)
    );
    
    private AdyenFeeCalculator() {
    }

    public static List<Fee> generateFeeList(AdyenFeeTransferType adyenFeeTransferType, long feeAmount,
                                            long paymentOrRefundGatewayFeeInPence,
                                            long fraudAvoidanceFeeInPence) {
        return switch (adyenFeeTransferType) {
            case FIXED_COMMISSION -> getFeeListBasedOnFixedCommission(feeAmount, paymentOrRefundGatewayFeeInPence,
                    fraudAvoidanceFeeInPence);
            case VARIABLE_COMMISSION -> List.of(Fee.of(TRANSACTION, feeAmount, VARIABLE));
            case INTERCHANGE -> List.of(Fee.of(TRANSACTION, feeAmount, INTERCHANGE));
            case SCHEME_FEE -> List.of(Fee.of(TRANSACTION, feeAmount, SCHEME_FEE));
            case UNSUPPORTED -> throw new IllegalArgumentException("Cannot calculate fees for " + adyenFeeTransferType);
        };
    }

    private static List<Fee> getFeeListBasedOnFixedCommission(long fixedCommission, long gatewayFee,
                                                              long fraudProtectionFee) {
        long fixedCommissionWithoutGatewayAndFraudProtectionFee = fixedCommission - (gatewayFee + fraudProtectionFee);

        if (fixedCommissionWithoutGatewayAndFraudProtectionFee < 0) {
            LOGGER.atError()
                    .setMessage("Adyen's fixed commission for charge is less than the sum of gateway and fraud protection fees")
                    .addKeyValue("fixed_commission", fixedCommission)
                    .log();
            throw new IllegalArgumentException(
                    "Adyen's fixed commission for charge is less than the sum of gateway and fraud protection fees"
            );
        }

        List<Fee> feeList = new ArrayList<>();
        feeList.add(Fee.of(GATEWAY, gatewayFee));
        feeList.add(Fee.of(FRAUD_PROTECTION, fraudProtectionFee));

        if (fixedCommissionWithoutGatewayAndFraudProtectionFee > 0) {
            feeList.add(Fee.of(TRANSACTION, fixedCommissionWithoutGatewayAndFraudProtectionFee, FIXED));
        }
        return feeList;
    }

    public static boolean hasAllExpectedFeeRecordsForAdyen(List<Fee> fees) {
        if(fees == null || fees.isEmpty()) {
            return false;
        }
        
        Set<FeeType> foundFeeTypes = EnumSet.noneOf(FeeType.class);
        Set<FeeSubType> transactionFeeSubTypes = EnumSet.noneOf(FeeSubType.class);

        for (Fee fee : fees) {
            if(fee == null || fee.feeType() == null) {
                return  false;
            }
            foundFeeTypes.add(fee.feeType());

            if (fee.feeType() == TRANSACTION && fee.feeSubType() != null) {
                transactionFeeSubTypes.add(fee.feeSubType());
            }
        }

        return foundFeeTypes.containsAll(REQUIRED_ADYEN_FEE_TYPES) && hasValidTransactionFeeSubTypes(transactionFeeSubTypes);
    }

    private static boolean hasValidTransactionFeeSubTypes(Set<FeeSubType> transactionFeeSubTypes) {
        return VALID_ADYEN_TRANSACTION_FEE_SUB_TYPE_COMBINATION.stream().anyMatch(transactionFeeSubTypes::equals);
    }
}
