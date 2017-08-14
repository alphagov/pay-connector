package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundHistory;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.Optional;
import java.util.*;

@Transactional
public class RefundDao extends JpaDao<RefundEntity> {

    @Inject
    public RefundDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundEntity> findById(long id) {
        return super.findById(RefundEntity.class, id);
    }

    public Optional<RefundEntity> findByProviderAndReference(String provider, String reference) {

        String query = "SELECT refund FROM RefundEntity refund " +
                "JOIN ChargeEntity charge ON refund.chargeEntity.id = charge.id " +
                "JOIN GatewayAccountEntity gatewayAccount ON charge.gatewayAccount.id = gatewayAccount.id " +
                "WHERE refund.reference = :reference AND gatewayAccount.gatewayName = :provider";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("reference", reference)
                .setParameter("provider", provider)
                .getResultList().stream().findFirst();
    }

    public List<RefundHistory> searchHistoryByChargeId(Long chargeId) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, history_start_date, history_end_date " +
                "FROM refunds_history r " +
                "WHERE charge_id = ?1";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeId)
                .getResultList();
    }
}
