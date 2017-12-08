package uk.gov.pay.connector.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
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

    /**
     * Used to calculate the MAX(id) for {@link PaymentRequestEntity}
     *
     * @return zero if null result or a {@link Long} greater than zero if any found
     */
    public Long findMaxId() {
        final Long singleResult = entityManager.get()
                .createQuery("SELECT MAX(p.id) FROM PaymentRequestEntity p", Long.class)
                .getSingleResult();
        return singleResult == null ? 0 : singleResult;
    }
}
