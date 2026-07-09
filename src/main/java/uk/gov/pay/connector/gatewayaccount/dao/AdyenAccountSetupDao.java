package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupStatus;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTask;
import uk.gov.pay.connector.gatewayaccount.model.AdyenAccountSetupTaskEntity;

import java.util.List;

public class AdyenAccountSetupDao extends JpaDao<AdyenAccountSetupTaskEntity> {

    @Inject
    public AdyenAccountSetupDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<AdyenAccountSetupTaskEntity> findByGatewayAccountIdAndCredentialId(long gatewayAccountId, long gatewayAccountCredentialsId) {
        String query = "SELECT s FROM AdyenAccountSetupTaskEntity s WHERE s.gatewayAccount.id = :gatewayAccountId AND s.gatewayAccountCredential.id = :gatewayAccountCredentialsId ";

        return entityManager
                .get()
                .createQuery(query, AdyenAccountSetupTaskEntity.class)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("gatewayAccountCredentialsId", gatewayAccountCredentialsId)
                .getResultList();
    }

    public boolean isTaskPresentForGatewayAccountAndCredentialId(long gatewayAccountId, long gatewayAccountCredentialsId, AdyenAccountSetupTask task) {
        String query = "SELECT COUNT(s) FROM AdyenAccountSetupTaskEntity s " +
                "WHERE s.gatewayAccount.id = :gatewayAccountId " +
                "AND s.gatewayAccountCredential.id = :gatewayAccountCredentialsId " +
                "AND s.task = :task";

        long count = (long) entityManager
                .get()
                .createQuery(query)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("task", task)
                .setParameter("gatewayAccountCredentialsId", gatewayAccountCredentialsId)
                .getSingleResult();

        return count > 0;
    }

    public void updateTaskStatus(Long gatewayAccountId, Long gatewayAccountCredentialsId, AdyenAccountSetupTask task, AdyenAccountSetupStatus status) {
        String query = "UPDATE AdyenAccountSetupTaskEntity s " +
                "SET s.status = :status " + 
                "WHERE s.gatewayAccount.id = :gatewayAccountId " +
                "AND s.gatewayAccountCredential.id = :gatewayAccountCredentialsId " +
                "AND s.task = :task";

        entityManager
                .get()
                .createQuery(query)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("task", task)
                .setParameter("gatewayAccountCredentialsId", gatewayAccountCredentialsId)
                .setParameter("status", status)
                .executeUpdate();
    }
}
