package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.transaction.RefundTransactionEntity;
import uk.gov.pay.connector.service.PaymentGatewayName;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public class RefundTransactionDao extends JpaDao<RefundTransactionEntity> {
    @Inject
    protected RefundTransactionDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundTransactionEntity> findByProviderAndReference(PaymentGatewayName provider, String reference) {
        String query = "SELECT refund FROM RefundTransactionEntity refund " +
                "WHERE refund.refundReference = :reference AND " +
                "refund.paymentRequest.gatewayAccount.gatewayName = :provider";

        return entityManager.get()
                .createQuery(query, RefundTransactionEntity.class)
                .setParameter("reference", reference)
                .setParameter("provider", provider.getName())
                .getResultList().stream().findFirst();
    }

    public Optional<RefundTransactionEntity> findByExternalId(String refundExternalId) {
        String query = "SELECT t FROM RefundTransactionEntity t " +
                "WHERE t.refundExternalId = :refundExternalId";

        return entityManager.get()
                .createQuery(query, RefundTransactionEntity.class)
                .setParameter("refundExternalId", refundExternalId)
                .getResultList().stream().findFirst();
    }
}
