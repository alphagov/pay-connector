package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.ChargeEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.Optional;

public class ChargeJpaDao extends JpaDao<ChargeEntity> {

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public <ID> Optional<ChargeEntity> findById(final ID id) {
        return super.findById(ChargeEntity.class, id);
    }

    public Optional<ChargeEntity> findByGatewayTransactionIdAndProvider(String transactionId, String paymentProvider) {
        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery("select c from ChargeEntity c where c.gatewayTransactionId = :gatewayTransactionId and c.gatewayAccount.gatewayName = :paymentProvider", ChargeEntity.class);

        query.setParameter("gatewayTransactionId", transactionId);
        query.setParameter("paymentProvider", paymentProvider);

        return Optional.ofNullable(query.getSingleResult());
    }

}
