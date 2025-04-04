package uk.gov.pay.connector.refund.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.common.model.domain.UTCDateTimeConverter;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;

@Transactional
public class RefundDao extends JpaDao<RefundEntity> {

    @Inject
    public RefundDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<RefundEntity> findById(Long refundId) {
        return super.findById(RefundEntity.class, refundId);
    }

    public Optional<RefundEntity> findByChargeExternalIdAndGatewayTransactionId(String chargeExternalId, String gatewayTransactionId) {

        String query = "SELECT refund FROM RefundEntity refund " +
                "WHERE refund.gatewayTransactionId = :gatewayTransactionId AND refund.chargeExternalId = :chargeExternalId";

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("gatewayTransactionId", gatewayTransactionId)
                .setParameter("chargeExternalId", chargeExternalId)
                .getResultList().stream().findFirst();
    }

    public Optional<RefundHistory> getRefundHistoryByRefundExternalIdAndRefundStatus(String refundExternalId, RefundStatus refundStatus) {
        String query = "SELECT rh.id, rh.external_id, rh.amount, rh.status, rh.created_date, " +
                "rh.version, rh.history_start_date, rh.history_end_date, rh.user_external_id, " +
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

        String query = "SELECT id, external_id, amount, status, created_date, version, " +
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
        String query = "SELECT r.id, r.external_id, r.amount, r.status, r.created_date, r.version, " +
                "history_start_date, history_end_date, user_external_id, r.gateway_transaction_id, " +
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

        String query = "SELECT id, external_id, amount, status, created_date, version, " +
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

    public Long findMaxId() {
        String query = "SELECT r.id FROM RefundEntity r ORDER BY r.id DESC";

        return entityManager.get()
                .createQuery(query, Long.class)
                .setMaxResults(1)
                .getSingleResult();
    }

    public Optional<RefundEntity> findRefundToExpunge(int minimumAgeOfRefundInDays, int excludeRefundsParityCheckedWithInDays) {
        String query = "SELECT r FROM RefundEntity r" +
                " WHERE (r.parityCheckDate is null or r.parityCheckDate < :parityCheckedBeforeDate)" +
                " AND r.createdDate < :createdBeforeDate " +
                " ORDER BY r.createdDate asc";

        ZonedDateTime parityCheckedBeforeDate = ZonedDateTime.now(UTC)
                .minusDays(excludeRefundsParityCheckedWithInDays);
        ZonedDateTime createdBeforeDate = ZonedDateTime.now(UTC)
                .minusDays(minimumAgeOfRefundInDays);

        return entityManager.get()
                .createQuery(query, RefundEntity.class)
                .setParameter("parityCheckedBeforeDate", parityCheckedBeforeDate)
                .setParameter("createdBeforeDate", createdBeforeDate)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    public void expungeRefund(String externalId) {
        entityManager.get()
                .createNativeQuery("delete from emitted_events where resource_type = ?1 AND resource_external_id = ?2")
                .setParameter(1, ResourceType.REFUND.getLowercase())
                .setParameter(2, externalId)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from refunds_history where external_id = ?1")
                .setParameter(1, externalId)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from refunds where external_id = ?1")
                .setParameter(1, externalId)
                .executeUpdate();
    }

    public List<RefundHistory> getRefundHistoryByRefundExternalId(String refundExternalId) {
        String query = "SELECT id, external_id, amount, status, created_date, version, " +
                "history_start_date, history_end_date, user_external_id, gateway_transaction_id, user_email, charge_external_id " +
                "FROM refunds_history r " +
                "WHERE external_id = ?1 ";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, refundExternalId)
                .getResultList();
    }

    public void updateParityCheckStatus(String externalId, ZonedDateTime parityCheckDate, ParityCheckStatus parityCheckStatus) {
        Query query = entityManager.get().createNativeQuery("update refunds " +
                " set parity_check_status = ?1, parity_check_date = ?2" +
                " where external_id = ?3");

        UTCDateTimeConverter utcDateTimeConverter = new UTCDateTimeConverter();

        query.setParameter(1, parityCheckStatus.toString())
                .setParameter(2, utcDateTimeConverter.convertToDatabaseColumn(parityCheckDate))
                .setParameter(3, externalId)
                .executeUpdate();
    }

    public List<RefundEntity> findByParityCheckStatus(ParityCheckStatus parityCheckStatus, int pageSize, Long lastProcessedId) {
        return entityManager.get()
                .createQuery("SELECT r FROM RefundEntity r WHERE r.id > :lastProcessedId " +
                        " AND r.parityCheckStatus = :parityCheckStatus ORDER BY r.id", RefundEntity.class)
                .setParameter("parityCheckStatus", parityCheckStatus)
                .setParameter("lastProcessedId", lastProcessedId)
                .setMaxResults(pageSize)
                .getResultList();
    }
}
