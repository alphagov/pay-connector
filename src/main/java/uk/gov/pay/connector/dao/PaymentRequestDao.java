package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;

import javax.persistence.EntityManager;

@Transactional
public class PaymentRequestDao extends JpaDao<PaymentRequestEntity> {

    @Inject
    public PaymentRequestDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
