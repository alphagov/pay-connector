package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
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

    public Optional<GatewayAccountCredentialsEntity> findByExternalId(String externalId) {
        String query = "SELECT ce FROM GatewayAccountCredentialsEntity ce " +
                "WHERE ce.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, GatewayAccountCredentialsEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }
}
