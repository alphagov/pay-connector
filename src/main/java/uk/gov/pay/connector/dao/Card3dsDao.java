package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.Card3dsEntity;

import javax.persistence.EntityManager;

@Transactional
public class Card3dsDao extends JpaDao<Card3dsEntity> {

    @Inject
    public Card3dsDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
