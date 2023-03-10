package uk.gov.pay.connector.idempotency.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.idempotency.model.IdempotencyEntity;

import javax.persistence.EntityManager;
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
}
