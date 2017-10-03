package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.spike.TransactionEventEntity;

@Transactional
public class TransactionEventDao extends JpaDao<TransactionEventEntity> {

    @Inject
    public TransactionEventDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
