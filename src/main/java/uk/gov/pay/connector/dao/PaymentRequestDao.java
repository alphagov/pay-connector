package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import java.util.Optional;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import uk.gov.pay.connector.model.domain.TokenEntity;
import uk.gov.pay.connector.model.spike.PaymentRequestEntity;

@Transactional
public class PaymentRequestDao extends JpaDao<PaymentRequestEntity> {

    @Inject
    public PaymentRequestDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<PaymentRequestEntity> findByPaymentId(String paymentRequestId) {
        return entityManager.get()
                .createQuery("SELECT p FROM PaymentRequestEntity p WHERE p.id= :paymentRequestId", PaymentRequestEntity.class)
                .setParameter("paymentRequestId", paymentRequestId)
                .getResultList().stream()
                .findFirst();
    }
}
