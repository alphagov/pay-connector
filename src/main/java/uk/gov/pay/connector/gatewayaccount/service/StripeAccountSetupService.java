package uk.gov.pay.connector.gatewayaccount.service;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.gatewayaccount.dao.StripeAccountSetupDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetup;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupUpdateRequest;

import java.util.List;

public class StripeAccountSetupService {

    private final StripeAccountSetupDao stripeAccountSetupDao;

    @Inject
    public StripeAccountSetupService(StripeAccountSetupDao stripeAccountSetupDao) {
        this.stripeAccountSetupDao = stripeAccountSetupDao;
    }

    public StripeAccountSetup getCompletedTasks(long gatewayAccountId) {
        StripeAccountSetup stripeAccountSetup = new StripeAccountSetup();
        stripeAccountSetupDao.findByGatewayAccountId(gatewayAccountId)
                .forEach(stripeAccountSetupTaskEntity -> {
                    switch (stripeAccountSetupTaskEntity.getTask()) {
                        case BANK_ACCOUNT:
                            stripeAccountSetup.setBankAccountCompleted(true);
                            break;
                        case RESPONSIBLE_PERSON:
                            stripeAccountSetup.setResponsiblePersonCompleted(true);
                            break;
                        case VAT_NUMBER:
                            stripeAccountSetup.setVatNumberCompleted(true);
                            break;
                        case COMPANY_NUMBER:
                            stripeAccountSetup.setCompanyNumberCompleted(true);
                            break;
                        case DIRECTOR:
                            stripeAccountSetup.setDirectorCompleted(true);
                            break;
                        case GOVERNMENT_ENTITY_DOCUMENT:
                            stripeAccountSetup.setGovernmentEntityDocument(true);
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

    @Transactional
    public void update(GatewayAccountEntity gatewayAccountEntity, List<StripeAccountSetupUpdateRequest> updates) {
        updates.forEach(stripeAccountSetupUpdateRequest -> {
            long gatewayAccountId = gatewayAccountEntity.getId();
            StripeAccountSetupTask task = stripeAccountSetupUpdateRequest.getTask();
            if (stripeAccountSetupUpdateRequest.isCompleted()) {
                if (!stripeAccountSetupDao.isTaskCompletedForGatewayAccount(gatewayAccountId, task)) {
                    StripeAccountSetupTaskEntity stripeAccountSetupTaskEntity = new StripeAccountSetupTaskEntity(gatewayAccountEntity, task);
                    stripeAccountSetupDao.persist(stripeAccountSetupTaskEntity);
                }
            } else {
                stripeAccountSetupDao.removeCompletedTaskForGatewayAccount(gatewayAccountId, task);
            }
        });
    }

    @Transactional
    public void completeTestAccountSetup(GatewayAccountEntity gatewayAccountEntity) {
        if (gatewayAccountEntity.isStripeTestAccount()) {
            List.of(StripeAccountSetupTask.values()).forEach(task -> {
                stripeAccountSetupDao.persist(new StripeAccountSetupTaskEntity(gatewayAccountEntity, task));
            });
        } else {
            throw new IllegalArgumentException("Gateway account type must be TEST and gateway name must be STRIPE");
        }
    }
}
