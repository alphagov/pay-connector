package uk.gov.pay.connector.refund.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TemporalType;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;


@Transactional
public class RefundDao extends JpaDao<RefundEntity> {

    @Inject
    public RefundDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundEntity> findById(Long refundId) {
        return super.findById(RefundEntity.class, refundId);
    }

    public Optional<RefundEntity> findByProviderAndReference(String provider, String reference) {

        String query = "SELECT refund FROM RefundEntity refund " +
                "JOIN ChargeEntity charge ON refund.chargeExternalId = charge.externalId " +
                "JOIN GatewayAccountEntity gatewayAccount ON charge.gatewayAccount.id = gatewayAccount.id " +
                "WHERE refund.reference = :reference AND gatewayAccount.gatewayName = :provider";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("reference", reference)
                .setParameter("provider", provider)
                .getResultList().stream().findFirst();
    }

    public Optional<RefundHistory> getRefundHistoryByRefundExternalIdAndRefundStatus(String refundExternalId, RefundStatus refundStatus) {
        String query = "SELECT rh.id, rh.external_id, rh.amount, rh.status, rh.charge_id, rh.created_date, " +
                "rh.version, rh.reference, rh.history_start_date, rh.history_end_date, rh.user_external_id, " +
                "rh.gateway_transaction_id, rh.charge_external_id, rh.user_email " +
                "FROM refunds_history rh " +
                "WHERE rh.external_id = ?1 AND rh.status = ?2";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, refundExternalId)
                .setParameter(2, refundStatus.getValue())
                .getResultList()
                .stream()
                .findFirst();

    }

    public List<RefundHistory> searchHistoryByChargeExternalId(String chargeExternalId) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, " +
                "history_start_date, history_end_date, user_external_id, gateway_transaction_id, user_email, charge_external_id " +
                "FROM refunds_history r " +
                "WHERE charge_external_id = ?1 AND status != ?2";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeExternalId)
                .setParameter(2, RefundStatus.CREATED.getValue())
                .getResultList();
    }

    public List<RefundHistory> searchAllHistoryByChargeExternalId(String chargeExternalId) {
        String query = "SELECT r.id, r.external_id, r.amount, r.status, r.charge_id, r.created_date, r.version, " +
                "r.reference, history_start_date, history_end_date, user_external_id, r.gateway_transaction_id, " +
                "r.charge_external_id AS charge_external_id, r.user_email " +
                " FROM refunds_history r " +
                " WHERE charge_external_id = ?1";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeExternalId)
                .getResultList();
    }

    public Optional<RefundEntity> findByExternalId(String externalId) {
        String query = "SELECT refund FROM RefundEntity refund " +
                "WHERE refund.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }

    public List<RefundHistory> getRefundHistoryByDateRange(ZonedDateTime startDate, ZonedDateTime endDate, int page, int size) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, " +
                "       history_start_date, history_end_date, user_external_id, gateway_transaction_id, user_email " +
                " FROM refunds_history rh " +
                " WHERE rh.history_start_date >= ?1 AND rh.history_start_date <= ?2" +
                " order by rh.history_start_date asc " +
                " limit ?3 offset ?4";

        int offset = (page - 1) * size;
        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, Date.from(startDate.toInstant()), TemporalType.TIMESTAMP)
                .setParameter(2, Date.from(endDate.toInstant()), TemporalType.TIMESTAMP)
                .setParameter(3, size)
                .setParameter(4, offset)
                .getResultList();
    }

    public List<RefundEntity> findRefundsByChargeExternalId(String chargeExternalId) {
        String query = "SELECT refund FROM RefundEntity refund " +
                "WHERE refund.chargeExternalId = :chargeExternalId ORDER BY refund.createdDate ASC";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("chargeExternalId", chargeExternalId)
                .getResultList();
    }
}
