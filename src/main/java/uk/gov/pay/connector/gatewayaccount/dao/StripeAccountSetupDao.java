package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.StripeAccountSetupTaskEntity;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;

public class StripeAccountSetupDao extends JpaDao<StripeAccountSetupTaskEntity> {

    @Inject
    public StripeAccountSetupDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<StripeAccountSetupTaskEntity> findByGatewayAccountId(long gatewayAccountId) {
        String query = "SELECT s FROM StripeAccountSetupTaskEntity s WHERE s.gatewayAccount.id = :gatewayAccountId";

        return entityManager
                .get()
                .createQuery(query, StripeAccountSetupTaskEntity.class)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList();
    }

    public boolean isTaskCompletedForGatewayAccount(long gatewayAccountId, StripeAccountSetupTask task) {
        String query = "SELECT COUNT(s) FROM StripeAccountSetupTaskEntity s WHERE s.gatewayAccount.id = :gatewayAccountId AND s.task = :task";

        long count = (long) entityManager
                .get()
                .createQuery(query)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("task", task)
                .getSingleResult();
        
        return count > 0;
    }
    
    public void removeCompletedTaskForGatewayAccount(long gatewayAccountId, StripeAccountSetupTask task) {
        String query = "DELETE FROM StripeAccountSetupTaskEntity s WHERE s.gatewayAccount.id = :gatewayAccountId AND s.task = :task";
        
        entityManager
                .get()
                .createQuery(query)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("task", task)
                .executeUpdate();
    }

}
