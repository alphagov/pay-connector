package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class GatewayAccountJpaDao extends JpaDao<GatewayAccountEntity> {

    @Inject
    public GatewayAccountJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
