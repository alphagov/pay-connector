package uk.gov.pay.connector.charge.util;

import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.Transaction;

public class FeeNetCalculator {
    private FeeNetCalculator() {
    }
   
    // @TODO(sfount) this essentially rebuilds the `total_amount` column that is calculated at the service level, if this
    //               was stored as part of the entity (calculation column?) the same value could be used
    public static Long getNetAmountFor(ChargeEntity charge) {  
        return charge.getAmount() + charge.getCorporateSurcharge().orElse(0L) - charge.getFeeAmount().orElse(0L);
    }
    
    public static Long getNetAmountFor(Transaction transaction) {  
        return transaction.getAmount() + transaction.getCorporateCardSurcharge().orElse(0L) - transaction.getFeeAmount().orElse(0L);
    }
}
