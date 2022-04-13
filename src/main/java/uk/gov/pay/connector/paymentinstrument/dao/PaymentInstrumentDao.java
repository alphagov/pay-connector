package uk.gov.pay.connector.paymentinstrument.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@Transactional
public class PaymentInstrumentDao extends JpaDao<PaymentInstrumentEntity> {
    
    @Inject
    public PaymentInstrumentDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
