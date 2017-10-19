package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.PaymentRequestEntity;
import javax.persistence.EntityManager;
import java.util.Optional;


@Transactional
public class PaymentRequestDao extends JpaDao<PaymentRequestEntity> {

    @Inject
    public PaymentRequestDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<PaymentRequestEntity> findByExternalId(String externalId) {
        String query = "SELECT p FROM PaymentRequestEntity p " +
                "WHERE p.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, PaymentRequestEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }

    public Optional<PaymentRequestEntity> updateChargeTransactionStatus(String externalId, ChargeStatus newChargeStatus) {
        return findByExternalId(externalId).map(paymentRequestEntity -> {
            paymentRequestEntity.getChargeTransaction().setStatus(newChargeStatus);

            return paymentRequestEntity;
        });
    }
}
