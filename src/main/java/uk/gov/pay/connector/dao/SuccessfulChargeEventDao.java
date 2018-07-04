package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.events.SuccessfulChargeEvent;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class SuccessfulChargeEventDao extends JpaDao<SuccessfulChargeEvent> {
    
    @Inject
    public SuccessfulChargeEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
