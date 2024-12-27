package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Optional;

import static uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialState.ACTIVE;

@Transactional
public class GatewayAccountCredentialsDao extends JpaDao<GatewayAccountCredentialsEntity> {

    @Inject
    public GatewayAccountCredentialsDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    @Override
    public void persist(GatewayAccountCredentialsEntity gatewayAccountCredentialsEntity) {
        super.persist(gatewayAccountCredentialsEntity);
    }

    public Optional<GatewayAccountCredentialsEntity> findById(Long id) {
        return super.findById(GatewayAccountCredentialsEntity.class, id);
    }

    public boolean hasActiveCredentials(Long gatewayAccountId) {
        String query = "SELECT COUNT(gace) FROM GatewayAccountCredentialsEntity gace " +
                " WHERE gace.gatewayAccountEntity.id = :gatewayAccountId AND gace.state = :state";

        long count = (long) entityManager
                .get()
                .createQuery(query)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .setParameter("state", ACTIVE)
                .getSingleResult();

        return count > 0;
    }

    public Optional<GatewayAccountCredentialsEntity> findByExternalIdAndGatewayAccountId(String externalId, Long gatewayAccountId) {
        String query = "SELECT ce FROM GatewayAccountCredentialsEntity ce " +
                "WHERE ce.externalId = :externalId" +
                "  AND ce.gatewayAccountEntity.id = :gatewayAccountId";

        return entityManager.get()
                .createQuery(query, GatewayAccountCredentialsEntity.class)
                .setParameter("externalId", externalId)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList().stream().findFirst();
    }

    public Optional<GatewayAccountCredentialsEntity> findByCredentialsKeyValue(String key, String value) {
        String query = "SELECT * FROM gateway_account_credentials where credentials->>?1 = ?2";

        return entityManager.get()
                .createNativeQuery(query, GatewayAccountCredentialsEntity.class)
                .setParameter(1, key)
                .setParameter(2, value)
                .getResultList().stream().findFirst();
    }
}
