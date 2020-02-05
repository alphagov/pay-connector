package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.Charge;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;

import java.util.Optional;

/**
 * Holder for utility methods used to calculate values around corporate surcharge
 */
public class CorporateCardSurchargeCalculator {
    private CorporateCardSurchargeCalculator() {
        // prevent Java for adding a public constructor
    }

    public static Long getTotalAmountFor(Charge charge) {
        return charge.getCorporateSurcharge()
                .map(surcharge -> surcharge + charge.getAmount())
                .orElseGet(charge::getAmount);
    }

    public static Long getTotalAmountFor(ChargeEntity chargeEntity) {
        return chargeEntity.getCorporateSurcharge()
                .map(surcharge -> surcharge + chargeEntity.getAmount())
                .orElseGet(chargeEntity::getAmount);
    }

    public static Optional<Long> getCorporateCardSurchargeFor(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        long surcharge = FixedCorporateCardSurchargeCalculator.calculate(authCardDetails, chargeEntity);
        return surcharge > 0 ? Optional.of(surcharge) : Optional.empty();
    }
}
