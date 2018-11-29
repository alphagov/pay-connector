package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.gateway.model.AuthCardDetails;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

class FixedCorporateCardSurchargeCalculator {

    static long calculate(AuthCardDetails authCardDetails, ChargeEntity chargeEntity) {
        if (authCardDetails.isCorporateCard()) {
            GatewayAccountEntity gatewayAccount = chargeEntity.getGatewayAccount();
            switch (authCardDetails.getPayersCardPrepaidStatus()) {
                case NOT_PREPAID:
                    switch (authCardDetails.getPayersCardType()) {
                        case CREDIT:
                            return gatewayAccount.getCorporateNonPrepaidCreditCardSurchargeAmount();
                        case DEBIT:
                            return gatewayAccount.getCorporateNonPrepaidDebitCardSurchargeAmount();
                        case CREDIT_OR_DEBIT:
                            return 0;
                    }
                case PREPAID:
                    switch (authCardDetails.getPayersCardType()) {
                        case CREDIT:
                            return gatewayAccount.getCorporatePrepaidCreditCardSurchargeAmount();
                        case DEBIT:
                            return gatewayAccount.getCorporatePrepaidDebitCardSurchargeAmount();
                        case CREDIT_OR_DEBIT:
                            return 0;
                    }
                case UNKNOWN:
                    return 0;
            }
        }
        return 0;
    }
}
