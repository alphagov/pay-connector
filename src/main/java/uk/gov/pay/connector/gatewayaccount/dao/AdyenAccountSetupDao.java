package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;

import java.util.List;

public class AdyenAccountSetupDao extends JpaDao<AdyenAccountSetupTaskEntity> {

    @Inject
    public AdyenAccountSetupDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<AdyenAccountSetupTaskEntity> findByGatewayAccountIdAndCredentialId(long gatewayAccountId) {
        String query = "SELECT s FROM AdyenAccountSetupTaskEntity s WHERE s.gatewayAccount.id = :gatewayAccountId";

        return entityManager
                .get()
                .createQuery(query, AdyenAccountSetupTaskEntity.class)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList();
    }
}
