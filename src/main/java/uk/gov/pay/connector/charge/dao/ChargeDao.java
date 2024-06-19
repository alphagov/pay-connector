package uk.gov.pay.connector.charge.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.charge.model.domain.ParityCheckStatus;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.events.model.ResourceType;
import uk.gov.pay.connector.gatewayaccount.model.GatewayAccountType;
import uk.gov.service.payments.commons.model.AuthorisationMode;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.AUTHORISATION_3DS_REQUIRED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED;
import static uk.gov.pay.connector.charge.model.domain.ChargeStatus.CAPTURE_APPROVED_RETRY;

@Transactional
public class ChargeDao extends JpaDao<ChargeEntity> {

    private static final String STATUS = "status";
    private static final String CREATED_DATE = "createdDate";
    private static final String UPDATED_DATE = "updatedDate";
    private static final String FIND_CAPTURE_CHARGES_WHERE_CLAUSE =
            "WHERE (c.status=:captureApprovedStatus OR c.status=:captureApprovedRetryStatus)" +
                    "AND NOT EXISTS (" +
                    "  SELECT ce FROM ChargeEventEntity ce WHERE " +
                    "    ce.chargeEntity = c AND " +
                    "    ce.status = :eventStatus AND " +
                    "    ce.updated >= :cutoffDate " +
                    ") ";

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
        String query = "SELECT te.chargeEntity FROM TokenEntity te WHERE te.token=:tokenId AND te.used=false";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("tokenId", tokenId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<ChargeEntity> findByGatewayTransactionId(String gatewayTransactionId) {
        String query = "SELECT c from ChargeEntity c WHERE c.gatewayTransactionId=:gatewayTransactionId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("gatewayTransactionId", gatewayTransactionId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<ChargeEntity> findByGatewayTransactionIdAndAccount(Long accountId, String gatewayTransactionId) {
        String query = "SELECT c from ChargeEntity c WHERE c.gatewayTransactionId=:gatewayTransactionId" +
                " and c.gatewayAccount.id = :accountId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("gatewayTransactionId", gatewayTransactionId)
                .setParameter("accountId", accountId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<ChargeEntity> findByExternalIdAndGatewayAccount(String chargeExternalId, Long accountId) {

        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE c.externalId = :externalId " +
                "AND c.gatewayAccount.id = :accountId";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("externalId", chargeExternalId)
                .setParameter("accountId", accountId)
                .getResultList().stream().findFirst();
    }

    public Optional<ChargeEntity> findByExternalIdAndServiceIdAndAccountType(String chargeExternalId, String serviceId, GatewayAccountType accountType) {

        String query = "SELECT c FROM ChargeEntity c INNER JOIN GatewayAccountEntity g ON c.gatewayAccount.id = g.id " +
                "WHERE c.externalId = :externalId " +
                "AND c.serviceId = :serviceId " +
                "AND g.type = :accountType";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("externalId", chargeExternalId)
                .setParameter("serviceId", serviceId)
                .setParameter("accountType", accountType)
                .getResultList().stream().findFirst();
    }

    public Optional<ChargeEntity> findByProviderAndTransactionId(String provider, String transactionId) {

        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE c.gatewayTransactionId = :gatewayTransactionId " +
                "AND c.paymentProvider = :provider";

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("gatewayTransactionId", transactionId)
                .setParameter("provider", provider).getResultList().stream().findFirst();
    }

    public List<ChargeEntity> findBeforeDateWithStatusIn(Instant date, List<ChargeStatus> statuses) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<ChargeEntity> cq = cb.createQuery(ChargeEntity.class);
        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);

        List<Predicate> predicates = buildParamPredicates(cb, charge, date, statuses);
        cq.select(charge)
                .where(predicates.toArray(new Predicate[]{}))
                .orderBy(cb.desc(charge.get(CREATED_DATE)));
        Query query = entityManager.get().createQuery(cq);

        return query.getResultList();
    }

    public List<ChargeEntity> findChargesByCreatedUpdatedDatesAndWithStatusIn(Instant createdBeforeDate,
                                                                              Instant updatedBeforeDate,
                                                                              List<ChargeStatus> statuses) {
        CriteriaBuilder cb = entityManager.get().getCriteriaBuilder();
        CriteriaQuery<ChargeEntity> cq = cb.createQuery(ChargeEntity.class);
        Root<ChargeEntity> charge = cq.from(ChargeEntity.class);

        List<Predicate> predicates = buildParamPredicates(cb, charge, createdBeforeDate, statuses);

        if (updatedBeforeDate != null) {
            predicates.add(cb.or(
                    cb.isNull(charge.get(UPDATED_DATE)),
                    cb.lessThan(charge.get(UPDATED_DATE), updatedBeforeDate)
            ));
        }

        cq.select(charge)
                .where(predicates.toArray(new Predicate[]{}))
                .orderBy(cb.desc(charge.get(CREATED_DATE)));
        Query query = entityManager.get().createQuery(cq);

        return query.getResultList();
    }

    private List<Predicate> buildParamPredicates(CriteriaBuilder cb, Root<ChargeEntity> charge,
                                                 Instant toDate, List<ChargeStatus> internalStates) {
        List<Predicate> predicates = new ArrayList<>();

        if (internalStates != null && !internalStates.isEmpty()) {
            predicates.add(charge.get(STATUS).in(internalStates));
        }
        if (toDate != null) {
            predicates.add(cb.lessThan(charge.get(CREATED_DATE), toDate));
        }

        return predicates;
    }

    public int countChargesForImmediateCapture(Duration notAttemptedWithin) {
        String query = "SELECT count(c) FROM ChargeEntity c " + FIND_CAPTURE_CHARGES_WHERE_CLAUSE;

        ZonedDateTime utcCutoffThreshold = ZonedDateTime.now()
                .minus(notAttemptedWithin)
                .withZoneSameInstant(ZoneId.of("UTC"));

        var count = (Number) entityManager.get()
                .createQuery(query)
                .setParameter("captureApprovedStatus", CAPTURE_APPROVED.getValue())
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY.getValue())
                .setParameter("eventStatus", CAPTURE_APPROVED_RETRY)
                .setParameter("cutoffDate", utcCutoffThreshold)
                .getSingleResult();
        return count.intValue();
    }

    public int countCaptureRetriesForChargeExternalId(String externalId) {
        String query = "SELECT count(ce) FROM ChargeEventEntity ce WHERE " +
                "    ce.chargeEntity.externalId = :externalId AND " +
                "    (ce.status = :captureApprovedStatus OR ce.status = :captureApprovedRetryStatus)";

        return ((Number) entityManager.get()
                .createQuery(query)
                .setParameter("externalId", externalId)
                .setParameter("captureApprovedStatus", CAPTURE_APPROVED)
                .setParameter("captureApprovedRetryStatus", CAPTURE_APPROVED_RETRY)
                .getSingleResult()).intValue();
    }

    public int count3dsRequiredEventsForChargeExternalId(String externalId) {
        String query = "SELECT count(ce) FROM ChargeEventEntity ce WHERE " +
                "    ce.chargeEntity.externalId = :externalId AND " +
                "    (ce.status = :authorisation3dsRequiredStatus)";

        return ((Number) entityManager.get()
                .createQuery(query)
                .setParameter("externalId", externalId)
                .setParameter("authorisation3dsRequiredStatus", AUTHORISATION_3DS_REQUIRED)
                .getSingleResult()).intValue();
    }

    public List<ChargeEntity> findByIdAndLimit(Long id, int limit) {
        return entityManager.get()
                .createQuery("SELECT c FROM ChargeEntity c WHERE c.id > :id ORDER BY c.id", ChargeEntity.class)
                .setParameter("id", id)
                .setMaxResults(limit)
                .getResultList();
    }

    public Long findMaxId() {
        String query = "SELECT c.id FROM ChargeEntity c ORDER BY c.id DESC";

        return entityManager.get()
                .createQuery(query, Long.class)
                .setMaxResults(1)
                .getSingleResult();
    }

    public List<ChargeEntity> findByParityCheckStatus(ParityCheckStatus parityCheckStatus, int size, Long lastProcessedId) {
        return entityManager.get()
                .createQuery("SELECT c FROM ChargeEntity c WHERE c.id > :lastProcessedId AND c.parityCheckStatus = :parityCheckStatus ORDER BY c.id", ChargeEntity.class)
                .setParameter("parityCheckStatus", parityCheckStatus)
                .setParameter("lastProcessedId", lastProcessedId)
                .setMaxResults(size)
                .getResultList();
    }

    public Optional<ChargeEntity> findChargeToExpunge(int minimumAgeOfChargeInDays,
                                                      int excludeChargesParityCheckedWithInDays) {
        String query = "SELECT c FROM ChargeEntity c " +
                "WHERE (c.parityCheckDate is null or c.parityCheckDate < :parityCheckedBeforeDate)" +
                " AND c.createdDate < :createdBeforeDate " +
                " ORDER BY c.createdDate asc";

        ZonedDateTime parityCheckedBeforeDate = ZonedDateTime.now()
                .minus(Duration.ofDays(excludeChargesParityCheckedWithInDays))
                .withZoneSameInstant(ZoneId.of("UTC"));

        Instant createdBeforeDate = Instant.now().minus(Duration.ofDays(minimumAgeOfChargeInDays));

        return entityManager.get()
                .createQuery(query, ChargeEntity.class)
                .setParameter("parityCheckedBeforeDate", parityCheckedBeforeDate)
                .setParameter("createdBeforeDate", createdBeforeDate)
                .setMaxResults(1)
                .getResultList().stream().findFirst();
    }

    public void expungeCharge(Long id, String externalId) {

        entityManager.get()
                .createNativeQuery("delete from charge_events where charge_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from tokens where charge_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from emitted_events where resource_type = ?1 AND resource_external_id = ?2")
                .setParameter(1, ResourceType.PAYMENT.getLowercase())
                .setParameter(2, externalId)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from fees where charge_id = ?1")
                .setParameter(1, id)
                .executeUpdate();

        entityManager.get()
                .createNativeQuery("delete from charges where id = ?1")
                .setParameter(1, id)
                .executeUpdate();
    }

    public List<ChargeEntity> findWithPaymentProviderAndStatusIn(String provider, List<ChargeStatus> statuses, int limit) {
        return entityManager.get()
                .createQuery("SELECT c FROM ChargeEntity c WHERE c.paymentProvider = :provider AND c.status in :statuses", ChargeEntity.class)
                .setParameter("provider", provider)
                .setParameter("statuses", statuses)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<ChargeEntity> findWithPaymentProvidersStatusesAndAuthorisationModesIn(List<String> providers, List<ChargeStatus> statuses,
                                                                                      List<AuthorisationMode> authorisationModes, int limit) {
        return entityManager.get()
                .createQuery("SELECT c FROM ChargeEntity c WHERE c.paymentProvider IN :providers AND c.status IN :statuses" +
                        " AND c.authorisationMode in :authorisationModes", ChargeEntity.class)
                .setParameter("providers", providers)
                .setParameter("statuses", statuses)
                .setParameter("authorisationModes", authorisationModes)
                .setMaxResults(limit)
                .getResultList();
    }
}
