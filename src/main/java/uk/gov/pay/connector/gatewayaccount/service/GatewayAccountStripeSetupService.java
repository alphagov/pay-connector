package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.gatewayaccount.dao.GatewayAccountStripeSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetup;

public class GatewayAccountStripeSetupService {

    private final GatewayAccountStripeSetupDao gatewayAccountStripeSetupDao;

    @Inject
    public GatewayAccountStripeSetupService(GatewayAccountStripeSetupDao gatewayAccountStripeSetupDao) {
        this.gatewayAccountStripeSetupDao = gatewayAccountStripeSetupDao;
    }
    
    public GatewayAccountStripeSetup getCompletedTasks(long gatewayAccountId) {
        GatewayAccountStripeSetup gatewayAccountStripeSetup = new GatewayAccountStripeSetup();
        gatewayAccountStripeSetupDao.findByGatewayAccountId(gatewayAccountId)
                .stream()
                .forEach(gatewayAccountStripeSetupTaskEntity -> {
                    switch(gatewayAccountStripeSetupTaskEntity.getTask()) {
                        case BANK_ACCOUNT:
                            gatewayAccountStripeSetup.setBankAccountCompleted(true);
                            break;
                        case RESPONSIBLE_PERSON:
                            gatewayAccountStripeSetup.setResponsiblePersonCompleted(true);
                            break;
                        case ORGANISATION_DETAILS:
                            gatewayAccountStripeSetup.setOrganisationDetailsCompleted(true);
                            break;
                        default:
                            // Code doesn’t handle this task
                    }
                });
        return gatewayAccountStripeSetup;
    }

}
