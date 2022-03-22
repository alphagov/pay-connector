package uk.gov.pay.connector.paymentinstrument.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;

public class PaymentInstrumentDao extends JpaDao<PaymentInstrumentEntity> {

    @Inject
    public PaymentInstrumentDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }
}
