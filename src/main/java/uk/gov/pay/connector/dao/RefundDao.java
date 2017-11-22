package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.domain.RefundHistory;
import uk.gov.pay.connector.model.domain.RefundStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Transactional
public class RefundDao extends JpaDao<RefundEntity> {

    private static final String ID = "id";
    private static final String STATUS = "status";
    private static final String CREATED_DATE = "createdDate";
    private static final String CHARGE_ENTITY = "chargeEntity";
    private static final String GATEWAY_ACCOUNT = "gatewayAccount";

    @Inject
    public RefundDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundEntity> findById(Long refundId) {
        return super.findById(RefundEntity.class, refundId);
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

    public List<RefundEntity> findByAccountBetweenDatesWithStatusIn(Long gatewayAccountId,
                                                                    ZonedDateTime from, ZonedDateTime to,
                                                                    List<RefundStatus> statuses) {
        CriteriaBuilder criteriaBuilder = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<RefundEntity> criteriaQuery = criteriaBuilder.createQuery(RefundEntity.class);
        Root<RefundEntity> refund = criteriaQuery.from(RefundEntity.class);
        criteriaQuery
                .select(refund)
                .where(
                        criteriaBuilder.equal(refund.get(CHARGE_ENTITY).get(GATEWAY_ACCOUNT).get(ID), gatewayAccountId),
                        criteriaBuilder.greaterThanOrEqualTo(refund.get(CREATED_DATE), from),
                        criteriaBuilder.lessThan(refund.get(CREATED_DATE), to),
                        refund.get(STATUS).in(statuses));
        return entityManager.get().createQuery(criteriaQuery).getResultList();
    }

    public List<RefundHistory> searchHistoryByChargeId(Long chargeId) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, history_start_date, history_end_date, user_external_id " +
                "FROM refunds_history r " +
                "WHERE charge_id = ?1 AND status != ?2";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeId)
                .setParameter(2, RefundStatus.CREATED.getValue())
                .getResultList();
    }
}
