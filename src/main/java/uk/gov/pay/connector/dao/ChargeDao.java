package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;

@Transactional
public class ChargeDao extends JpaDao<ChargeEntity> {

    private static final String STATUS = "status";
    private static final String CREATED_DATE = "createdDate";
    private static final String GATEWAY_ACCOUNT = "gatewayAccount";
    private static final String CARD_DETAILS = "cardDetails";
    private static final String REFERENCE = "reference";
    private static final String EMAIL = "email";
    private static final String SQL_ESCAPE_SEQ = "\\\\";

    @Inject
    public ChargeDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public Optional<ChargeEntity> findById(Long chargeId) {
        return super.findById(ChargeEntity.class, chargeId);
    }

    public Optional<ChargeEntity> findByExternalId(String externalId) {

        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE c.externalId = :externalId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("externalId", externalId)
                .getResultList().stream().findFirst();
    }

    public Optional<ChargeEntity> findByTokenId(String tokenId) {
        String query = "SELECT te.chargeEntity FROM TokenEntity te WHERE te.token=:tokenId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("tokenId", tokenId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<ChargeEntity> findByExternalIdAndGatewayAccount(String externalId, Long accountId) {

        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE c.externalId = :externalId " +
                "AND c.gatewayAccount.id = :accountId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("externalId", externalId)
                .setParameter("accountId", accountId)
                .getResultList().stream().findFirst();
    }

    public Optional<ChargeEntity> findByProviderAndTransactionId(String provider, String transactionId) {

        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayTransactionId = :gatewayTransactionId " +
                "AND c.gatewayAccount.gatewayName = :provider";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("gatewayTransactionId", transactionId)
                .setParameter("provider", provider).getResultList().stream().findFirst();
    }

    public List<ChargeEntity> findBeforeDateWithStatusIn(ZonedDateTime date, List<ChargeStatus> statuses) {
        ChargeSearchParams params = new ChargeSearchParams()
                .withToDate(date)
                .withInternalStates(statuses);
        return findAllBy(params);
    }

    public List<ChargeEntity> findByAccountBetweenDatesWithStatusIn(Long gatewayAccountId,
                                                                    ZonedDateTime from, ZonedDateTime to,
                                                                    List<ChargeStatus> statuses) {
        ChargeSearchParams params = new ChargeSearchParams()
                .withGatewayAccountId(gatewayAccountId)
                .withFromDate(from)
                .withToDate(to)
                .withInternalStates(statuses);
        return findAllBy(params);
    }

    public List<ChargeEntity> findAllBy(ChargeSearchParams params) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<ChargeEntity> cq = cb.createQuery(ChargeEntity.class);
        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);

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
        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);
        List<Predicate> predicates = buildParamPredicates(params, cb, charge);

        cq.select(cb.count(charge));
        cq.where(predicates.toArray(new Predicate[]{}));
        return entityManager.get().createQuery(cq).getSingleResult();
    }

    private List<Predicate> buildParamPredicates(ChargeSearchParams params, CriteriaBuilder cb, Root<ChargeEntity> charge) {
        List<Predicate> predicates = new ArrayList<>();
        if (params.getCardHolderName() != null && StringUtils.isNotBlank(params.getCardHolderName().toString()))
            predicates.add(likePredicate(cb, charge.get(CARD_DETAILS).get("cardHolderName"), params.getCardHolderName().toString()));
        if (params.getLastDigitsCardNumber() != null) 
            predicates.add(cb.equal(charge.get(CARD_DETAILS).get("lastDigitsCardNumber"), params.getLastDigitsCardNumber()));
        if (params.getFirstDigitsCardNumber() != null) 
            predicates.add(cb.equal(charge.get(CARD_DETAILS).get("firstDigitsCardNumber"), params.getFirstDigitsCardNumber()));
        if (params.getGatewayAccountId() != null)
            predicates.add(cb.equal(charge.get(GATEWAY_ACCOUNT).get("id"), params.getGatewayAccountId()));
        if (params.getReference() != null && StringUtils.isNotBlank(params.getReference().toString()))
            predicates.add(likePredicate(cb, charge.get(REFERENCE), params.getReference().toString()));
        if (StringUtils.isNotBlank(params.getEmail()))
            predicates.add(likePredicate(cb, charge.get(EMAIL), params.getEmail()));
        if (params.getInternalStates() != null && !params.getInternalStates().isEmpty())
            predicates.add(charge.get(STATUS).in(params.getInternalStates()));
        if (!params.getCardBrands().isEmpty()) {
            predicates.add(charge.get(CARD_DETAILS).get("cardBrand").in(params.getCardBrands()));
        }
        if (params.getFromDate() != null)
            predicates.add(cb.greaterThanOrEqualTo(charge.get(CREATED_DATE), params.getFromDate()));
        if (params.getToDate() != null)
            predicates.add(cb.lessThan(charge.get(CREATED_DATE), params.getToDate()));

        return predicates;
    }

    private Predicate likePredicate(CriteriaBuilder cb, Path<String> expression, String element) {
        String escapedReference = element
                .replaceAll("\\\\", SQL_ESCAPE_SEQ + "\\\\")
                .replaceAll("_", SQL_ESCAPE_SEQ + "_")
                .replaceAll("%", SQL_ESCAPE_SEQ + "%");

        return cb.like(cb.lower(expression), '%' + escapedReference.toLowerCase() + '%');
    }
    
    private static final String FIND_CAPTURE_CHARGES_WHERE_CLAUSE = 
            "WHERE (c.status=:captureApprovedStatus OR c.status=:captureApprovedRetryStatus)"+
            "AND NOT EXISTS (" +
            "  SELECT ce FROM ChargeEventEntity ce WHERE " +
            "    ce.chargeEntity = c AND " +
            "    ce.status = :eventStatus AND " +
            "    ce.updated >= :cutoffDate " +
            ") ";
    
    public int countChargesForImmediateCapture(Duration notAttemptedWithin) {
        String query = "SELECT count(c) FROM ChargeEntity c " + FIND_CAPTURE_CHARGES_WHERE_CLAUSE;

        Number count = (Number) entityManager.get()
                .createQuery(query)
                .setParameter("captureApprovedStatus", CAPTURE_APPROVED.getValue())
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY.getValue())
                .setParameter("eventStatus", CAPTURE_APPROVED_RETRY)
                .setParameter("cutoffDate", ZonedDateTime.now().minus(notAttemptedWithin))
                .getSingleResult();
        return count.intValue();
    }

    public List<ChargeEntity> findChargesForCapture(int maxNumberOfCharges, Duration notAttemptedWithin) {
        String query = "SELECT c FROM ChargeEntity c " + FIND_CAPTURE_CHARGES_WHERE_CLAUSE + "ORDER BY c.createdDate ASC";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setMaxResults(maxNumberOfCharges)
                .setParameter("captureApprovedStatus", CAPTURE_APPROVED.getValue())
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY.getValue())
                .setParameter("eventStatus", CAPTURE_APPROVED_RETRY)
                .setParameter("cutoffDate", ZonedDateTime.now().minus(notAttemptedWithin))
                .getResultList();
    }
    
    public int countChargesAwaitingCaptureRetry(Duration notAttemptedWithin) {
        String query = "SELECT count(c) FROM ChargeEntity c WHERE c.status=:captureApprovedRetryStatus "+
                "AND EXISTS (" +
                "  SELECT ce FROM ChargeEventEntity ce WHERE " +
                "    ce.chargeEntity = c AND " +
                "    ce.status = :eventStatus AND " +
                "    ce.updated >= :cutoffDate " +
                ") ";

        Number count = (Number) entityManager.get()
                .createQuery(query)
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY.getValue())
                .setParameter("eventStatus", CAPTURE_APPROVED_RETRY)
                .setParameter("cutoffDate", ZonedDateTime.now().minus(notAttemptedWithin))
                .getSingleResult();
        return count.intValue();
    }

    public int countCaptureRetriesForCharge(long chargeId) {
        String query = "SELECT count(ce) FROM ChargeEventEntity ce WHERE " +
                "    ce.chargeEntity.id = :chargeId AND " +
                "    (ce.status = :captureApprovedStatus OR ce.status = :captureApprovedRetryStatus)";

        return ((Number) entityManager.get()
                .createQuery(query)
                .setParameter("chargeId", chargeId)
                .setParameter("captureApprovedStatus", CAPTURE_APPROVED)
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY)
                .getSingleResult()).intValue();
    }

    public List<ChargeEntity> findByIdAndLimit(Long id, int limit) {
            return entityManager.get()
                    .createQuery("SELECT c FROM ChargeEntity c WHERE c.id > :id ORDER BY c.id", ChargeEntity.class)
                    .setParameter("id", id)
                    .setMaxResults(limit)
                    .getResultList();
        }
}
