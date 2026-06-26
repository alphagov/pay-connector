package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import uk.gov.pay.connector.gatewayaccount.dao.AdyenAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetup;


public class AydenAccountSetupService {

    private final AdyenAccountSetupDao adyenAccountSetupDao;

    @Inject
    public AydenAccountSetupService(AdyenAccountSetupDao adyenAccountSetupDao) {
        this.adyenAccountSetupDao = adyenAccountSetupDao;
    }

    public AdyenAccountSetup getCompletedTasks(long gatewayAccountId, long credentialId) {
        AdyenAccountSetup adyenAccountSetup = new AdyenAccountSetup();
        adyenAccountSetupDao.findByGatewayAccountIdAndCredentialId(gatewayAccountId)
                .forEach(adyenAccountSetupTaskEntity -> {
                    switch (adyenAccountSetupTaskEntity.getTask()) {
                        case BANK_ACCOUNT:
                            adyenAccountSetup.setBankAccountStatus(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case RESPONSIBLE_PERSON:
                            adyenAccountSetup.setResponsiblePersonStatus(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case VAT_NUMBER:
                            adyenAccountSetup.setVatNumberStatus(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case COMPANY_NUMBER:
                            adyenAccountSetup.setCompanyNumberStatus(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case DIRECTOR:
                            adyenAccountSetup.setDirectorStatus(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case GOVERNMENT_ENTITY_DOCUMENT:
                            adyenAccountSetup.setGovernmentEntityDocument(adyenAccountSetupTaskEntity.getStatus());
                            break;
                        case ORGANISATION_DETAILS:
                            adyenAccountSetup.setOrganisationDetailsStatus((adyenAccountSetupTaskEntity.getStatus()));
                            break;
                        default:
                            // Code doesn’t handle this task
                    }
                });
        return adyenAccountSetup;
    }
}
