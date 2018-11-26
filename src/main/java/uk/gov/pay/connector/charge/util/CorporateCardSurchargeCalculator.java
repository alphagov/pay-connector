package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Transaction;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gateway.model.PayersCardType;
import uk.gov.pay.connector.gateway.model.PayersPrepaidCardType;

import java.util.Optional;

/**
 * Holder for utility methods used to calculate values around corporate surcharge
 */
public class CorporateCardSurchargeCalculator {
    private CorporateCardSurchargeCalculator() {
        // prevent Java for adding a public constructor
    }

    public static Long getTotalAmountFor(ChargeEntity charge) {
        return charge.getCorporateSurcharge()
                .map(surcharge -> surcharge + charge.getAmount())
                .orElseGet(charge::getAmount);
    }

    /**
     * Utility method to calculate total amount for a charge that has a corporate
     * surcharge.
     * <p>
     * Output from this method is the sum between {@link Transaction#corporateSurcharge}
     * and {@link Transaction#amount} when the corporate surcharge exists
     * or amount only, when corporate surcharge is zero or {@link null}
     *
     * @param transaction The {@link Transaction} for which to get the total amount
     * @return A {@link Long}
     */
    public static Long getTotalAmountFor(Transaction transaction) {
        return transaction.getCorporateCardSurcharge()
                .map(surcharge -> surcharge + transaction.getAmount())
                .orElseGet((transaction::getAmount));
    }

    public static Optional<Long> getCorporateCardSurchargeFor(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        if (authCardDetails.isCorporateCard()) {
            if (isCreditSurcharge(authCardDetails, chargeEntity)) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporateCreditCardSurchargeAmount());
            }

            if (isDebitSurcharge(authCardDetails, chargeEntity)) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporateDebitCardSurchargeAmount());
            }

            if (isPrepaidCreditSurcharge(authCardDetails, chargeEntity)) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporatePrepaidCreditCardSurchargeAmount());
            }

            if (isPrepaidDebitSurcharge(authCardDetails, chargeEntity)) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporatePrepaidDebitCardSurchargeAmount());
            }
        }
        return Optional.empty();
    }

    private static boolean isCreditSurcharge(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        return PayersCardType.CREDIT.equals(authCardDetails.getPayersCardType()) &&
                PayersPrepaidCardType.NOT_PREPAID.equals(authCardDetails.getPayersPrepaidCardType()) &&
                chargeEntity.getGatewayAccount().getCorporateCreditCardSurchargeAmount() > 0;
    }

    private static boolean isDebitSurcharge(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        return PayersCardType.DEBIT.equals(authCardDetails.getPayersCardType()) &&
                PayersPrepaidCardType.NOT_PREPAID.equals(authCardDetails.getPayersPrepaidCardType()) &&
                chargeEntity.getGatewayAccount().getCorporateDebitCardSurchargeAmount() > 0;
    }

    private static boolean isPrepaidCreditSurcharge(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        return PayersCardType.CREDIT.equals(authCardDetails.getPayersCardType()) &&
                PayersPrepaidCardType.PREPAID.equals(authCardDetails.getPayersPrepaidCardType()) &&
                chargeEntity.getGatewayAccount().getCorporatePrepaidCreditCardSurchargeAmount() > 0;
    }

    private static boolean isPrepaidDebitSurcharge(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        return PayersCardType.DEBIT.equals(authCardDetails.getPayersCardType()) &&
                PayersPrepaidCardType.PREPAID.equals(authCardDetails.getPayersPrepaidCardType()) &&
                chargeEntity.getGatewayAccount().getCorporatePrepaidDebitCardSurchargeAmount() > 0;
    }

}
