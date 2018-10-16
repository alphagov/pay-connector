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
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
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

    public List<RefundHistory> searchAllHistoryByChargeId(Long chargeId) {

        String query = "SELECT id, external_id, amount, status, charge_id, created_date, version, reference, history_start_date, history_end_date, user_external_id " +
                "FROM refunds_history r " +
                "WHERE charge_id = ?1";

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
            Long firstResult = (params.getPage() - 1) * params.getDisplaySize();
            query.setFirstResult(firstResult.intValue());
            query.setMaxResults(params.getDisplaySize().intValue());
        }
        return query.getResultList();
    }
}
