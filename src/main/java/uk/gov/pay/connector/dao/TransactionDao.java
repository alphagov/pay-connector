package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.RefundEntity;
import uk.gov.pay.connector.model.spike.ChargeEntityNew;
import uk.gov.pay.connector.model.spike.RefundEntityNew;
import uk.gov.pay.connector.model.spike.TransactionEntity;
import uk.gov.pay.connector.model.spike.TransactionEntity.TransactionOperation;
import uk.gov.pay.connector.model.spike.TransactionEventEntity;
import uk.gov.pay.connector.model.spike.TransactionEventEntity.TransactionStatus;

@Transactional
public class TransactionDao<T> extends JpaDao<TransactionEntity> {

    private static final String STATUS = "status";
    private static final String CREATED_DATE = "createdDate";
    private static final String GATEWAY_ACCOUNT = "gatewayAccount";
    private static final String PAYMENT_REQUEST = "paymentRequest";
    private static final String CARD_DETAILS = "cardDetails";
    private static final String REFERENCE = "reference";
    private static final String EMAIL = "email";
    private static final String SQL_ESCAPE_SEQ = "\\\\";

    private TransactionEventDao transactionEventDao;

    @Inject
    public TransactionDao(final Provider<EntityManager> entityManager, TransactionEventDao transactionEventDao) {
        super(entityManager);
        this.transactionEventDao = transactionEventDao;
    }


    public Optional<TransactionEntity> findById(Long chargeId) {
        return super.findById(TransactionEntity.class, chargeId);
    }
    public List<TransactionEntity> findAllByPaymentRequestId(Long paymentRequestId) {

        String query = "SELECT c FROM TransactionEntity c " +
            "WHERE c.paymentRequest.id = :paymentRequestId ";

        return entityManager.get()
            .createQuery(query, TransactionEntity.class)
            .setParameter("paymentRequestId", paymentRequestId)
            .getResultList();
    }

    public Optional<TransactionEntity> findByExternalId(String externalId) {
        String query = "SELECT c FROM TransactionEntity c " +
            "JOIN PaymentRequestEntity pr ON c.paymentRequest.id = pr.id " +
            "WHERE pr.externalId = :externalId ";

        return entityManager.get()
            .createQuery(query, TransactionEntity.class)
            .setParameter("externalId", externalId)
            .getResultList().stream().findFirst();
    }

    private TypedQuery getQueryForEpdq(String reference) {
        String[] references = reference.split("/");
        String epdqPayId = references[0];
        String epdqPayIdSub = references[1];
        String query = "SELECT refund FROM RefundEntityNew refund " +
            "WHERE refund.epdqPayId = :epdqPayId AND refund.epdqPayIdSub = :epdqPayIdSub";

        return entityManager.get()
            .createQuery(query, RefundEntity.class)
            .setParameter("epdqPayId", epdqPayId)
            .setParameter("epdqPayIdSub", epdqPayIdSub);
    }

    public Optional<RefundEntityNew> findByProviderAndReference(String provider, String reference) {
        switch (provider) {
            case "epdq":
                return getQueryForEpdq(reference).getResultList().stream().findFirst();
            case "smartpay":
                String query = "SELECT refund FROM RefundEntityNew refund " +
                    "WHERE refund.smartpayPspReference = :smartpayPspReference";
                return entityManager.get()
                    .createQuery(query, RefundEntityNew.class)
                    .setParameter("smartpayPspReference", reference).getResultList().stream().findFirst();
            default:
                String anotherQuery = "SELECT refund FROM RefundEntityNew refund " +
                    "WHERE refund.externalId = :externalId";
                return entityManager.get()
                    .createQuery(anotherQuery, RefundEntityNew.class)
                    .setParameter("externalId", reference).getResultList().stream().findFirst();
        }
    }


    public Optional<RefundEntityNew> findRefundByExternalId(String externalId) {

        String query = "SELECT r FROM RefundEntityNew r " +
            "WHERE r.externalId = :externalId ";

        return entityManager.get()
            .createQuery(query, RefundEntityNew.class)
            .setParameter("externalId", externalId)
            .getResultList().stream().findFirst();
    }


    public Optional<? extends TransactionEntity> findByExternalIdAndGatewayAccount(String externalId, Long accountId) {

        String query = "SELECT c FROM TransactionEntity c " +
            "JOIN PaymentRequestEntity pr ON c.paymentRequest.id = pr.id " +
            "WHERE pr.externalId = :externalId " +
            "AND pr.gatewayAccount.id = :accountId";

        return entityManager.get()
            .createQuery(query, TransactionEntity.class)
            .setParameter("externalId", externalId)
            .setParameter("accountId", accountId)
            .getResultList().stream().findFirst();
    }

    public Optional<ChargeEntityNew> findByProviderAndTransactionId(String provider, String transactionId) {

        String query = "SELECT c FROM ChargeEntityNew c " +
            "JOIN PaymentRequestEntity pr ON c.paymentRequest.id = pr.id " +
            "WHERE c.gatewayTransactionId = :gatewayTransactionId " +
            "AND pr.gatewayAccount.gatewayName = :provider";

        return entityManager.get()
            .createQuery(query, ChargeEntityNew.class)
            .setParameter("gatewayTransactionId", transactionId)
            .setParameter("provider", provider).getResultList().stream().findFirst();
    }

    public void persist(TransactionEntity transactionEntity) {
        super.persist(transactionEntity);
        transactionEventDao.persist(TransactionEventEntity.from(transactionEntity, TransactionStatus.CREATED, transactionEntity.getCreatedDate()));
    }

    public List<? extends TransactionEntity> findBeforeDateWithStatusIn(ZonedDateTime date, List<ChargeStatus> statuses) {
        ChargeSearchParams params = new ChargeSearchParams()
            .withToDate(date)
            .withInternalChargeStatuses(statuses);
        return findAllBy(params);
    }

    public List<? extends TransactionEntity> findAllBy(ChargeSearchParams params) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<TransactionEntity> cq = cb.createQuery(TransactionEntity.class);
        Root<TransactionEntity> charge = cq.from(TransactionEntity.class);
        List<TransactionOperation> operations = new ArrayList<>();
        if (params.getTransactionOperation() == null) {
            operations.addAll(Arrays.asList(TransactionOperation.values()));
        } else {
            operations.add(params.getTransactionOperation());
        }
        List<Predicate> predicates = buildParamPredicates(params, cb, charge);
        cq.select(charge)
            .where(predicates.toArray(new Predicate[]{}))
            .orderBy(cb.desc(charge.get(CREATED_DATE)));
        Query query = entityManager.get().createQuery(cq);

        if (params.getPage() != null && params.getDisplaySize() != null) {
            Long firstResult = (params.getPage() - 1) * params.getDisplaySize(); // page coming from params is 1 based, so -1
            query.setFirstResult(firstResult.intValue());
            query.setMaxResults(params.getDisplaySize().intValue());
        }
        return query.getResultList();
    }

    public Long getTotalFor(ChargeSearchParams params) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<TransactionEntity> charge = cq.from(TransactionEntity.class);
        List<TransactionOperation> operations = new ArrayList<>();
        List<Predicate> predicates = buildParamPredicates(params, cb, charge);

        cq.select(cb.count(charge));
        cq.where(predicates.toArray(new Predicate[]{}));
        return entityManager.get().createQuery(cq).getSingleResult();
    }

    //todo
    public TransactionEntity mergeAndNotifyStatusHasChanged(TransactionEntity chargeEntity, Optional<ZonedDateTime> gatewayEventDate) {
        if (chargeEntity instanceof ChargeEntityNew && gatewayEventDate.isPresent()) {
            ((ChargeEntityNew) chargeEntity).setGatewayEventDate(gatewayEventDate.get());
        }
        TransactionEntity mergedCharge = super.merge(chargeEntity);
        transactionEventDao.persist(TransactionEventEntity.from(chargeEntity, TransactionStatus.fromString(chargeEntity.getStatus()), ZonedDateTime.now()));
        return mergedCharge;
    }

    public void notifyStatusHasChanged(TransactionEntity chargeEntity, Optional<ZonedDateTime> gatewayEventDate) {
        if (chargeEntity instanceof ChargeEntityNew && gatewayEventDate.isPresent()) {
            ((ChargeEntityNew) chargeEntity).setGatewayEventDate(gatewayEventDate.get());
        }
        transactionEventDao.persist(TransactionEventEntity.from(chargeEntity, TransactionStatus.fromString(chargeEntity.getStatus()), ZonedDateTime.now()));
    }

    private List<Predicate> buildParamPredicates(ChargeSearchParams params, CriteriaBuilder cb, Root<TransactionEntity> charge) {
        List<Predicate> predicates = new ArrayList<>();
        if (params.getGatewayAccountId() != null)
            predicates.add(cb.equal(charge.get(PAYMENT_REQUEST).get(GATEWAY_ACCOUNT).get("id"), params.getGatewayAccountId()));
        if (StringUtils.isNotBlank(params.getReference())){
            predicates.add(likePredicate(cb, charge.get(PAYMENT_REQUEST).get(REFERENCE), params.getReference()));
        }
        if (StringUtils.isNotBlank(params.getEmail())) {
            Root<ChargeEntityNew> treat = cb.treat(charge, ChargeEntityNew.class);
            predicates.add(likePredicate(cb, treat.get(EMAIL), params.getEmail()));
        }
        if (params.getInternalChargeStatuses() != null && !params.getInternalChargeStatuses().isEmpty())
            predicates.add(charge.get(STATUS).in(params.getInternalChargeStatuses()));
        if (StringUtils.isNotBlank(params.getCardBrand()))
            predicates.add(charge.get(CARD_DETAILS).get("cardBrand").in(params.getCardBrand()));
        if (params.getFromDate() != null)
            predicates.add(cb.greaterThanOrEqualTo(charge.get(CREATED_DATE), params.getFromDate()));
        if (params.getToDate() != null)
            predicates.add(cb.lessThan(charge.get(CREATED_DATE), params.getToDate()));

        return predicates;
    }

    private Predicate likePredicate(CriteriaBuilder cb, Path<String> expression, String element) {
        String escapedReference = element
            .replaceAll("_", SQL_ESCAPE_SEQ + "_")
            .replaceAll("%", SQL_ESCAPE_SEQ + "%");

        return cb.like(cb.lower(expression), '%' + escapedReference.toLowerCase() + '%');
    }
}
