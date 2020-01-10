package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

@Transactional
public class GatewayAccountDao extends JpaDao<GatewayAccountEntity> {

    @Inject
    public GatewayAccountDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<GatewayAccountEntity> findById(Long gatewayAccountId) {
        return super.findById(GatewayAccountEntity.class, gatewayAccountId);
    }
    
    public Optional<GatewayAccountEntity> findByNotificationCredentialsUsername(String username) {
        String query = "SELECT gae FROM GatewayAccountEntity gae " +
                "WHERE gae.notificationCredentials.userName = :username";


        return entityManager.get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("username", username)
                .getResultList().stream().findFirst();
    }

    public List<GatewayAccountEntity> list(List<Long> accountIds) {
        String query = "SELECT gae"
                       + " FROM GatewayAccountEntity gae"
                       + " WHERE gae.id IN :accountIds"
                       + " ORDER BY gae.id";

        return entityManager
                .get()
                .createQuery(query, GatewayAccountEntity.class)
                .setParameter("accountIds", accountIds)
                .getResultList();
    }

    public List<GatewayAccountEntity> listAll() {
        String query = "SELECT gae " +
                "FROM GatewayAccountEntity gae " +
                "ORDER BY gae.id";

        return entityManager
                .get()
                .createQuery(query, GatewayAccountEntity.class)
                .getResultList();
    }
}
