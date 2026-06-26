package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;


public class AydenAccountSetupService {

    private final AdyenAccountSetupDao adyenAccountSetupDao;

    @Inject
    public AydenAccountSetupService(AdyenAccountSetupDao adyenAccountSetupDao) {
        this.adyenAccountSetupDao = adyenAccountSetupDao;
    }

    public StripeAccountSetup getCompletedTasks(long gatewayAccountId, long credentialId) {
        StripeAccountSetup adyenAccountSetup = new StripeAccountSetup();
        adyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(gatewayAccountId)
                .forEach(adyenAccountSetupTaskEntity -> {
                    switch (adyenAccountSetupTaskEntity.getTask()) {
                        case BANK_ACCOUNT:
                            adyenAccountSetup.setBankAccountCompleted(true);
                            break;
                        case RESPONSIBLE_PERSON:
                            adyenAccountSetup.setResponsiblePersonCompleted(true);
                            break;
                        case VAT_NUMBER:
                            adyenAccountSetup.setVatNumberCompleted(true);
                            break;
                        case COMPANY_NUMBER:
                            adyenAccountSetup.setCompanyNumberCompleted(true);
                            break;
                        case DIRECTOR:
                            adyenAccountSetup.setDirectorCompleted(true);
                            break;
                        case GOVERNMENT_ENTITY_DOCUMENT:
                            adyenAccountSetup.setGovernmentEntityDocument(true);
                            break;
                        case ORGANISATION_DETAILS:
                            adyenAccountSetup.setOrganisationDetailsCompleted(true);
                            break;
                        default:
                            // Code doesn’t handle this task
                    }
                });
        return adyenAccountSetup;
    }
}
