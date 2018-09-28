package uk.gov.pay.connector.util.charge;

import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.PayersCardType;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

/**
 * Holder for utility methods used to calculate values around corporate surcharge
 */
public class CorporateSurchargeCalculator {
    /**
     * Will update field {@link ChargeEntity#corporateSurcharge} to whatever is stored in
     * {@link GatewayAccountEntity#corporateCreditCardSurchargeAmount} or
     * {@link GatewayAccountEntity#corporateDebitCardSurchargeAmount} based on the
     * {@link AuthCardDetails#payersCardType}
     * <p>
     * The logic here is that if these authorisation details are for a corporate card
     * and the gateway account has specified a value other than zero or null then this
     * charge will be updated
     *
     * @param authCardDetails An {@link AuthCardDetails} that is sent to be authorised
     * @param chargeEntity    A {@link ChargeEntity} for which {@param authCardDetails} are sent
     */
    public static void setCorporateSurchargeFor(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        final Boolean corporateCard = authCardDetails.isCorporateCard();
        if (corporateCard != null && corporateCard) {
            final PayersCardType payersCardType = authCardDetails.getPayersCardType();
            if (payersCardType != null) {
                final GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
                if (payersCardType.equals(PayersCardType.CREDIT) && gatewayAccount.getCorporateCreditCardSurchargeAmount() > 0) {
                    chargeEntity.setCorporateSurcharge(gatewayAccount.getCorporateCreditCardSurchargeAmount());
                } else if (payersCardType.equals(PayersCardType.DEBIT) && gatewayAccount.getCorporateDebitCardSurchargeAmount() > 0) {
                    chargeEntity.setCorporateSurcharge(gatewayAccount.getCorporateDebitCardSurchargeAmount());
                }
            }
        }
    }

    /**
     * Utility method to calculate total amount for a charge that has a corporate
     * surcharge.
     * <p>
     * Output from this method is the sum between {@link ChargeEntity#corporateSurcharge}
     * and {@link ChargeEntity#amount} when the corporate surcharge is greater than zero
     * or amount only, when corporate surcharge is zero or {@link null}
     *
     * @param charge The {@link ChargeEntity} for which to get the total amount
     * @return A {@link Long}
     */
    public static Long getTotalAmountFor(ChargeEntity charge) {
        if (charge.getCorporateSurcharge() != null && charge.getCorporateSurcharge() > 0) {
            return charge.getCorporateSurcharge() + charge.getAmount();
        } else {
            return charge.getAmount();
        }
    }
}
