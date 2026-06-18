package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;

public class AdyenAccountSetupDao extends JpaDao<AdyenAccountSetupTaskEntity> {

    @Inject
    public AdyenAccountSetupDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

}
