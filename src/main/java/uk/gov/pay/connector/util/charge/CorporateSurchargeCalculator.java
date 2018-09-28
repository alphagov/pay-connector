package uk.gov.pay.connector.util.charge;

import uk.gov.pay.connector.model.domain.AuthCardDetails;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.PayersCardType;

import java.util.Optional;

/**
 * Holder for utility methods used to calculate values around corporate surcharge
 */
public class CorporateSurchargeCalculator {
    public static Optional<Long> getCorporateSurchargeFor(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        if (authCardDetails.isCorporateCard()) {
            if (authCardDetails.getPayersCardType().equals(PayersCardType.CREDIT) &&
                    chargeEntity.getGatewayAccount().getCorporateCreditCardSurchargeAmount() > 0) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporateCreditCardSurchargeAmount());
            } else if (authCardDetails.getPayersCardType().equals(PayersCardType.DEBIT) &&
                    chargeEntity.getGatewayAccount().getCorporateDebitCardSurchargeAmount() > 0) {
                return Optional.of(chargeEntity.getGatewayAccount().getCorporateDebitCardSurchargeAmount());
            }
        }
        return Optional.empty();
    }


    public static Long getTotalAmountFor(ChargeEntity charge) {
        if (charge.getCorporateSurcharge().isPresent()) {
            return charge.getCorporateSurcharge().get() + charge.getAmount();
        }
        return charge.getAmount();
    }
}
