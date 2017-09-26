//package uk.gov.pay.connector.dao;
//
//import com.google.inject.Provider;
//import org.apache.commons.lang3.StringUtils;
//import uk.gov.pay.connector.model.domain.ChargeEntity;
//import uk.gov.pay.connector.model.domain.ChargeStatus;
//
//import javax.inject.Inject;
//import javax.persistence.EntityManager;
//import javax.persistence.Query;
//import javax.persistence.criteria.CriteriaBuilder;
//import javax.persistence.criteria.CriteriaQuery;
//import javax.persistence.criteria.Path;
//import javax.persistence.criteria.Predicate;
//import javax.persistence.criteria.Root;
//import java.time.ZonedDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//public class TransactionsDao extends JpaDao<TransactionEntity>{
//    private static final String PAYMENT_REQUEST_ID = "payment_request_id";
//    private static final String AMOUNT = "amount";
//    private static final String OPERATION = "operation";
//    private static final String CHARGE_GATEWAY_ID = "charge_gateway_id";
//    private static final String REFUND_SMARTPAY_PSPREFERENCE = "refund_smartpay_pspreference";
//    private static final String REFUND_EPDQ_PAYID = "refund_epdq_payid";
//    private static final String REFUND_EPDQ_PAYIDSUB = "refund_epdq_payidsub";
//    private static final String REFUND_EXTERNAL_ID = "refund_external_id";
//    private static final String REFUND_USER_EXTERNAL_ID = "refund_user_external_id";
//    private static final String SQL_ESCAPE_SEQ = "\\\\";
//
//    private EventsDao eventsDao;
//    private PaymentRequestDao paymentRequestDao;
//
//    @Inject
//    public TransactionsDao(final Provider<EntityManager> entityManager, EventsDao eventsDao, PaymentsRequestDao paymentsRequestDao) {
//        super(entityManager);
//        this.eventsDao = eventsDao;
//        this.paymentRequestDao = paymentsRequestDao;
//    }
//
//    public Optional<TransactionEntity> findById(Long chargeId) {
//        return super.findById(TransactionEntity.class, chargeId);
//    }
//
//    public List<ChargeEntity> findBeforeDateWithStatusIn(ZonedDateTime date, List<ChargeStatus> statuses) {
//        ChargeSearchParams params = new ChargeSearchParams()
//                .withToDate(date)
//                .withInternalChargeStatuses(statuses);
//        return findAllBy(params);
//    }
//
//    public List<ChargeEntity> findAllBy(ChargeSearchParams params) {
//        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
//        CriteriaQuery<ChargeEntity> cq = cb.createQuery(ChargeEntity.class);
//        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);
//
//        List<Predicate> predicates = buildParamPredicates(params, cb, charge);
//        cq.select(charge)
//                .where(predicates.toArray(new Predicate[]{}))
//                .orderBy(cb.desc(charge.get(CREATED_DATE)));
//        Query query = entityManager.get().createQuery(cq);
//
//        if (params.getPage() != null && params.getDisplaySize() != null) {
//            Long firstResult = (params.getPage() - 1) * params.getDisplaySize(); // page coming from params is 1 based, so -1
//            query.setFirstResult(firstResult.intValue());
//            query.setMaxResults(params.getDisplaySize().intValue());
//        }
//        return query.getResultList();
//    }
//
//
//    private List<Predicate> buildParamPredicates(ChargeSearchParams params, CriteriaBuilder cb, Root<ChargeEntity> charge) {
//        List<Predicate> predicates = new ArrayList<>();
//        if (params.getGatewayAccountId() != null)
//            predicates.add(cb.equal(charge.get(GATEWAY_ACCOUNT).get("id"), params.getGatewayAccountId()));
//        if (StringUtils.isNotBlank(params.getReference()))
//            predicates.add(likePredicate(cb, charge.get(REFERENCE), params.getReference()));
//        if (StringUtils.isNotBlank(params.getEmail()))
//            predicates.add(likePredicate(cb, charge.get(EMAIL), params.getEmail()));
//        if (params.getChargeStatuses() != null && !params.getChargeStatuses().isEmpty())
//            predicates.add(charge.get(STATUS).in(params.getChargeStatuses()));
//        if (StringUtils.isNotBlank(params.getCardBrand()))
//            predicates.add(charge.get(CARD_DETAILS).get("cardBrand").in(params.getCardBrand()));
//        if (params.getFromDate() != null)
//            predicates.add(cb.greaterThanOrEqualTo(charge.get(CREATED_DATE), params.getFromDate()));
//        if (params.getToDate() != null)
//            predicates.add(cb.lessThan(charge.get(CREATED_DATE), params.getToDate()));
//
//        return predicates;
//    }
//
//    private Predicate likePredicate(CriteriaBuilder cb, Path<String> expression, String element) {
//        String escapedReference = element
//                .replaceAll("_", SQL_ESCAPE_SEQ + "_")
//                .replaceAll("%", SQL_ESCAPE_SEQ + "%");
//
//        return cb.like(cb.lower(expression), '%' + escapedReference.toLowerCase() + '%');
//    }
//    public Optional<ChargeEntity> findByProviderAndTransactionId(String provider, String transactionId) {
//
//        String query = "SELECT c FROM ChargeEntity c " +
//                "WHERE c.gatewayTransactionId = :gatewayTransactionId " +
//                "AND c.paymentRequest.gatewayAccount.gatewayName = :provider";
//
//        return entityManager.get()
//                .createQuery(query, ChargeEntity.class)
//                .setParameter("gatewayTransactionId", transactionId)
//                .setParameter("provider", provider).getResultList().stream().findFirst();
//    }
//
//}
