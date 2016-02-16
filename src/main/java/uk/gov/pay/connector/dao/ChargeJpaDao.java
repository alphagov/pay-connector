package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.util.ChargeEventListener;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class ChargeJpaDao extends JpaDao<ChargeEntity> {

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
