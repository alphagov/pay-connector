package uk.gov.pay.connector.gatewayaccount.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountStripeSetupTaskEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

public class GatewayAccountStripeSetupDao extends JpaDao<GatewayAccountStripeSetupTaskEntity> {

    @Inject
    public GatewayAccountStripeSetupDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public List<GatewayAccountStripeSetupTaskEntity> findByGatewayAccountId(long gatewayAccountId) {
        String query = "SELECT g FROM GatewayAccountStripeSetupTaskEntity g WHERE g.gatewayAccount.id = :gatewayAccountId";

        return entityManager
                .get()
                .createQuery(query, GatewayAccountStripeSetupTaskEntity.class)
                .setParameter("gatewayAccountId", gatewayAccountId)
                .getResultList();
    }

}
