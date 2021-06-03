package uk.gov.pay.connector.gatewayaccountcredentials.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccountcredentials.model.GatewayAccountCredentialsEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

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
}
