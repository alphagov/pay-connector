package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.CardEntity;

import javax.persistence.EntityManager;

@Transactional
public class CardDao extends JpaDao<CardEntity> {

    @Inject
    protected CardDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

}
