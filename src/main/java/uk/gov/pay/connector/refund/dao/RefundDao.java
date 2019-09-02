package uk.gov.pay.connector.refund.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.dao.SearchParams;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.refund.model.domain.RefundEntity;
import uk.gov.pay.connector.refund.model.domain.RefundHistory;
import uk.gov.pay.connector.refund.model.domain.RefundStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Lists.newArrayList;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUNDED;
import static uk.gov.pay.connector.refund.model.domain.RefundStatus.REFUND_SUBMITTED;


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

    public Optional<RefundHistory> getRefundHistoryByRefundExternalIdAndRefundStatus(String refundExternalId, RefundStatus refundStatus) {
        String query = "SELECT rh.id, rh.external_id, rh.amount, rh.status, rh.charge_id, rh.created_date, " +
                "rh.version, rh.reference, rh.history_start_date, rh.history_end_date, rh.user_external_id, " +
                "rh.gateway_transaction_id, ch.external_id AS charge_external_id, ch.gateway_account_id " +
                "FROM refunds_history rh, charges ch " +
                "WHERE rh.external_id = ?1 AND rh.status = ?2 AND rh.charge_id = ch.id";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, refundExternalId)
                .setParameter(2, refundStatus.getValue())
                .getResultList()
                .stream()
                .findFirst();

    }

    public List<RefundHistory> searchHistoryByChargeId(Long chargeId) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, history_start_date, history_end_date, user_external_id, gateway_transaction_id " +
                "FROM refunds_history r " +
                "WHERE charge_id = ?1 AND status != ?2";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeId)
                .setParameter(2, RefundStatus.CREATED.getValue())
                .getResultList();
    }

    public List<RefundHistory> searchAllHistoryByChargeId(Long chargeId) {
        String query = "SELECT r.id, r.external_id, r.amount, r.status, charge_id, r.created_date, r.version, r.reference, history_start_date, history_end_date, user_external_id, r.gateway_transaction_id, c.external_id AS charge_external_id, c.gateway_account_id " +
                " FROM refunds_history r , charges c" +
                " WHERE charge_id = ?1" +
                " and r.charge_id = c.id";

        return entityManager.get()
                .createNativeQuery(query, "RefundEntityHistoryMapping")
                .setParameter(1, chargeId)
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

    public Long getTotalFor(SearchParams params) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<RefundEntity> refund = cq.from(RefundEntity.class);
        List<Predicate> predicates = buildParamPredicates(params, cb, refund);

        cq.select(cb.count(refund));
        cq.where(predicates.toArray(new Predicate[]{}));
        return entityManager.get().createQuery(cq).getSingleResult();
    }

    private List<Predicate> buildParamPredicates(SearchParams params, CriteriaBuilder cb, Root<RefundEntity> refundEntityRoot) {
        List<Predicate> predicates = new ArrayList<>();

        if (params.getGatewayAccountId() != null)
            predicates.add(cb.equal(refundEntityRoot.get(CHARGE_ENTITY).get(GATEWAY_ACCOUNT).get("id"), params.getGatewayAccountId()));
        if (params.getFromDate() != null)
            predicates.add(cb.greaterThanOrEqualTo(refundEntityRoot.get(CREATED_DATE), params.getFromDate()));
        if (params.getToDate() != null)
            predicates.add(cb.lessThan(refundEntityRoot.get(CREATED_DATE), params.getToDate()));

        return predicates;
    }

    public List<RefundEntity> findAllBy(SearchParams params) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<RefundEntity> cq = cb.createQuery(RefundEntity.class);
        Root<RefundEntity> refund = cq.from(RefundEntity.class);

        List<Predicate> predicates = buildParamPredicates(params, cb, refund);
        predicates.add(refund.get(STATUS).in(newArrayList(REFUND_SUBMITTED, REFUNDED)));
        cq.select(refund)
                .where(predicates.toArray(new Predicate[]{}))
                .orderBy(cb.desc(refund.get(CREATED_DATE)));
        Query query = entityManager.get().createQuery(cq);

        if (params.getPage() != null && params.getDisplaySize() != null) {
            long displaySize = params.getDisplaySize();
            long firstResult = (params.getPage() - 1) * displaySize;

            query.setFirstResult((int) firstResult);
            query.setMaxResults((int) displaySize);
        }
        return query.getResultList();
    }

    public List<RefundHistory> getRefundHistoryByDateRange(ZonedDateTime startDate, ZonedDateTime endDate, int page, int size) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, " +
                "       history_start_date, history_end_date, user_external_id, gateway_transaction_id " +
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
}
