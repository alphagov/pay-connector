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

    public Long findMaxId() {
        final Long singleResult = entityManager.get()
                .createQuery("SELECT MAX(c.id) FROM CardEntity c", Long.class)
                .getSingleResult();
        return singleResult == null ? 0 : singleResult;
    }
}
