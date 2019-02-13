package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;

public class StripeAccountSetupService {

    private final StripeAccountSetupDao stripeAccountSetupDao;

    @Inject
    public StripeAccountSetupService(StripeAccountSetupDao stripeAccountSetupDao) {
        this.stripeAccountSetupDao = stripeAccountSetupDao;
    }
    
    public StripeAccountSetup getCompletedTasks(long gatewayAccountId) {
        StripeAccountSetup stripeAccountSetup = new StripeAccountSetup();
        stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId)
                .stream()
                .forEach(stripeAccountSetupTaskEntity -> {
                    switch(stripeAccountSetupTaskEntity.getTask()) {
                        case BANK_ACCOUNT:
                            stripeAccountSetup.setBankAccountCompleted(true);
                            break;
                        case RESPONSIBLE_PERSON:
                            stripeAccountSetup.setResponsiblePersonCompleted(true);
                            break;
                        case ORGANISATION_DETAILS:
                            stripeAccountSetup.setOrganisationDetailsCompleted(true);
                            break;
                        default:
                            // Code doesn’t handle this task
                    }
                });
        return stripeAccountSetup;
    }

}
