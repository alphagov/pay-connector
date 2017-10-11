package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.CardholderDataEntity;

import javax.persistence.EntityManager;

@Transactional
public class CardholderDataDao extends JpaDao<CardholderDataEntity> {

    @Inject
    protected CardholderDataDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

}
