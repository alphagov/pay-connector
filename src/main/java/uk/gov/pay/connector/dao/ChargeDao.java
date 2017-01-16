package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.StringUtils;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Transactional
public class ChargeDao extends JpaDao<ChargeEntity> {

    private static final String STATUS = "status";
    private static final String CREATED_DATE = "createdDate";
    private static final String GATEWAY_ACCOUNT = "gatewayAccount";
    private static final String CARD_DETAILS = "cardDetails";
    private static final String REFERENCE = "reference";
    private static final String EMAIL = "email";
    public static final String SQL_ESCAPE_SEQ = "\\\\";

    private ChargeEventDao chargeEventDao;

    @Inject
    public ChargeDao(final Provider<EntityManager> entityManager, ChargeEventDao chargeEventDao) {
        super(entityManager);
        this.chargeEventDao = chargeEventDao;
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
        return findByExternalId(externalId).filter(charge -> charge.isAssociatedTo(accountId));
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

    public void persist(ChargeEntity chargeEntity) {
        super.persist(chargeEntity);
        chargeEventDao.persist(ChargeEventEntity.from(chargeEntity, ChargeStatus.CREATED, chargeEntity.getCreatedDate(), Optional.empty()));
    }

    public List<ChargeEntity> findBeforeDateWithStatusIn(ZonedDateTime date, List<ChargeStatus> statuses) {
        ChargeSearchParams params = new ChargeSearchParams()
                .withToDate(date)
                .withInternalChargeStatuses(statuses);
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

    public ChargeEntity mergeAndNotifyStatusHasChanged(ChargeEntity chargeEntity, Optional<ZonedDateTime> gatewayEventDate) {
        ChargeEntity mergedCharge = super.merge(chargeEntity);
        chargeEventDao.persist(ChargeEventEntity.from(chargeEntity, ChargeStatus.fromString(chargeEntity.getStatus()), ZonedDateTime.now(), gatewayEventDate));
        return mergedCharge;
    }

    private List<Predicate> buildParamPredicates(ChargeSearchParams params, CriteriaBuilder cb, Root<ChargeEntity> charge) {
        List<Predicate> predicates = new ArrayList<>();
        if (params.getGatewayAccountId() != null)
            predicates.add(cb.equal(charge.get(GATEWAY_ACCOUNT).get("id"), params.getGatewayAccountId()));
        if (StringUtils.isNotBlank(params.getReference()))
            predicates.add(likePredicate(cb, charge.get(REFERENCE), params.getReference()));
        if (StringUtils.isNotBlank(params.getEmail()))
            predicates.add(likePredicate(cb, charge.get(EMAIL), params.getEmail()));
        if (params.getChargeStatuses() != null && !params.getChargeStatuses().isEmpty())
            predicates.add(charge.get(STATUS).in(params.getChargeStatuses()));
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
