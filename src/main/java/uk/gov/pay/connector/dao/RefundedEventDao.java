package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.events.RefundedEvent;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class RefundedEventDao extends JpaDao<RefundedEvent> {
    
    @Inject
    public RefundedEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
