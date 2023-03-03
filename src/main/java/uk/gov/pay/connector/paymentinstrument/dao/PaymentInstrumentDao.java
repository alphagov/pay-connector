package uk.gov.pay.connector.paymentinstrument.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.paymentinstrument.model.PaymentInstrumentEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class PaymentInstrumentDao extends JpaDao<PaymentInstrumentEntity> {
    
    @Inject
    public PaymentInstrumentDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<PaymentInstrumentEntity> findByExternalId(String externalId) {

        String query = "SELECT p FROM PaymentInstrumentEntity p " +
                "WHERE p.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, PaymentInstrumentEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }
}
