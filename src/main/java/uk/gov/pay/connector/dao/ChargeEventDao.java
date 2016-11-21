package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class ChargeEventDao extends JpaDao<ChargeEventEntity> {

    @Inject
    public ChargeEventDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
