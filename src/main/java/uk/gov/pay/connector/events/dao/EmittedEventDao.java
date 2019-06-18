package uk.gov.pay.connector.events.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.events.EmittedEventEntity;

import javax.persistence.EntityManager;

@Transactional
public class EmittedEventDao extends JpaDao<EmittedEventEntity> {

    @Inject
    protected EmittedEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
