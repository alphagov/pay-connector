package uk.gov.pay.connector.fee.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.model.domain.FeeEntity;
import uk.gov.pay.connector.common.dao.JpaDao;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class FeeDao extends JpaDao<FeeEntity> {
    @Inject
    public FeeDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
