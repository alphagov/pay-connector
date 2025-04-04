package uk.gov.pay.connector.idempotency.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;

public class IdempotencyDao extends JpaDao<IdempotencyEntity> {
    @Inject
    public IdempotencyDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<IdempotencyEntity> findByGatewayAccountIdAndKey(Long gatewayAccountId, String key) {
        String query = "SELECT ie FROM IdempotencyEntity ie " +
                "WHERE ie.gatewayAccount.id = :gatewayAccountId " +
                "AND ie.key = :key";

        return entityManager.get()
                .createQuery(query, IdempotencyEntity.class)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("key", key)
                .getResultList().stream().findFirst();
    }

    public int deleteIdempotencyKeysOlderThanSpecifiedDateTime(Instant expiryDate) {
        String query = "DELETE FROM IdempotencyEntity ie WHERE ie.createdDate < :expiryDate";
        return entityManager.get()
                .createQuery(query, IdempotencyEntity.class)
                .setParameter("expiryDate", expiryDate)
                .executeUpdate();
    }

    public boolean idempotencyExistsByResourceExternalId(String resourceExternalId) {
        String query = "SELECT COUNT(ie) FROM IdempotencyEntity ie WHERE ie.resourceExternalId = :resourceExternalId";

        long count = (long) entityManager
                .get()
                .createQuery(query)
                .setParameter("resourceExternalId",resourceExternalId )
                .getSingleResult();

        return count > 0;
    }
}
