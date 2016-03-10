package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.model.domain.GatewayAccountEntity;

import javax.inject.Inject;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

@Transactional
public class ChargeJpaDao extends JpaDao<ChargeEntity> implements IChargeDao {

    private static final Logger logger = LoggerFactory.getLogger(ChargeJpaDao.class);

    private GatewayAccountJpaDao gatewayAccountDao;
    private EventJpaDao eventDao;

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager, GatewayAccountJpaDao gatewayAccountDao, EventJpaDao eventDao) {
        super(entityManager);
        this.eventDao = eventDao;
        this.gatewayAccountDao = gatewayAccountDao;
    }

    //TODO Remove this method in favour of findByProviderAndTransactionId
    private Optional<ChargeEntity> findByGatewayTransactionIdAndProvider(String transactionId, String paymentProvider) {
        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery("select c from ChargeEntity c where c.gatewayTransactionId = :gatewayTransactionId and c.gatewayAccount.gatewayName = :paymentProvider", ChargeEntity.class);

        query.setParameter("gatewayTransactionId", transactionId);
        query.setParameter("paymentProvider", paymentProvider);

        ChargeEntity result = null;

        try {
            result = query.getSingleResult();
        } catch (NoResultException e) {
            // This will be removed!
        }

        return Optional.ofNullable(result);
    }

    public List<ChargeEntity> findAllBy(ChargeSearchQuery searchQuery) {
        TypedQuery<ChargeEntity> query = searchQuery.apply(entityManager.get());
        return query.getResultList();
    }

    @Override
    public String saveNewCharge(String gatewayAccountId, Map<String, Object> charge) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(new Long(gatewayAccountId))
                .orElseThrow(() -> new PayDBIException(format("Could not create a new charge with Gateway accountId '%s'", gatewayAccountId)));

        ChargeEntity chargeEntity =
                new ChargeEntity(null,
                        new Long(charge.get("amount").toString()),
                        CREATED.getValue(),
                        null,
                        charge.get("return_url").toString(),
                        charge.get("description").toString(),
                        charge.get("reference").toString(),
                        gatewayAccountEntity);

        super.persist(chargeEntity);
        entityManager.get().flush();
        eventDao.persist(ChargeEventEntity.from(chargeEntity, CREATED, chargeEntity.getCreatedDate().toLocalDateTime()));
        return chargeEntity.getId().toString();
    }

    @Override
    public Optional<Map<String, Object>> findChargeForAccount(String chargeId, String accountId) {
        Optional<ChargeEntity> chargeEntityOpt = findById(new Long(chargeId));
        if (chargeEntityOpt.isPresent() && chargeEntityOpt.get().getGatewayAccount().getId().toString().equals(accountId)) {
            return Optional.of(buildChargeMap(chargeEntityOpt.get()));
        }
        return Optional.empty();
    }

    public Optional<ChargeEntity> findChargeForAccount(Long chargeId, String accountId) {
        return findById(chargeId).filter(charge -> charge.isAssociatedTo(accountId));
    }

    @Override
    public Optional<ChargeEntity> findChargeForAccount(Long chargeId, Long accountId) {
        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery("select c from ChargeEntity c "
                        + "join c.gatewayAccount ga "
                        + "where c.id = :chargeId "
                        + "and ga.id = :accountId", ChargeEntity.class);

        query.setParameter("chargeId", chargeId);
        query.setParameter("accountId", accountId);

        try {
            return Optional.ofNullable(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Map<String, Object>> findById(String chargeId) {
        Map<String, Object> chargeMap = null;
        Optional<ChargeEntity> chargeEntityOptional = findById(new Long(chargeId));

        if (chargeEntityOptional.isPresent()) {
            chargeMap = buildChargeMap(chargeEntityOptional.get());
        }
        return Optional.ofNullable(chargeMap);
    }

    @Override
    public Optional<ChargeEntity> findById(Long chargeId) {
        return super.findById(ChargeEntity.class, chargeId);
    }

    @Override

    public void updateGatewayTransactionId(String chargeId, String transactionId) {
        ChargeEntity charge = findById(new Long(chargeId))
                .orElseThrow(() -> new PayDBIException(format("Could not update charge '%s' with gateway transaction id %s", chargeId, transactionId)));
        charge.setGatewayTransactionId(transactionId);
    }

    @Override
    public void updateStatusWithGatewayInfo(String provider, String gatewayTransactionId, ChargeStatus newStatus) {
        ChargeEntity chargeEntity = findByGatewayTransactionIdAndProvider(gatewayTransactionId, provider)
                .orElseThrow(() -> new PayDBIException(format("Could not update charge (gateway_transaction_id: %s) with status %s", gatewayTransactionId, newStatus)));
        chargeEntity.setStatus(newStatus);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, newStatus, LocalDateTime.now()));
    }

    @Override
    public List<Map<String, Object>> findAllBy(String gatewayAccountId, String reference, ExternalChargeStatus status, String fromDate, String toDate) {
        ChargeSearchQuery searchQuery = new ChargeSearchQuery(new Long(gatewayAccountId));
        searchQuery.withReferenceLike(reference);
        searchQuery.withExternalStatus(status);
        searchQuery.withCreatedDateFrom(fromDate);
        searchQuery.withCreatedDateTo(toDate);

        List<ChargeEntity> chargeEntities = findAllBy(searchQuery);
        logger.info("found " + chargeEntities.size() + " charge records for the criteria");

        return chargeEntities
                .stream()
                .map(charge -> buildChargeMap(charge))
                .collect(toList());
    }

    @Override
    public void updateStatus(String chargeId, ChargeStatus newStatus) {
        updateStatus(new Long(chargeId), newStatus);
    }

    public ChargeEntity updateStatus(Long chargeId, ChargeStatus newStatus) {
        ChargeEntity chargeEntity = findById(chargeId)
                .orElseThrow(() -> new PayDBIException(format("Could not update charge '%s' with status %s, updated %d rows.", chargeId, newStatus, 0)));
        chargeEntity.setStatus(newStatus);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, newStatus, LocalDateTime.now()));
        return chargeEntity;
    }

    @Override
    public int updateNewStatusWhereOldStatusIn(String chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {
        return updateNewStatusWhereOldStatusIn(new Long(chargeId), newStatus, oldStatuses);
    }

    public int updateNewStatusWhereOldStatusIn(Long chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {

        //FIXME WIP. This method won't exist as soon this is switched to use JPA.
        // Just done to conform to common interface before switching dao implementations
        final int[] updated = {0};

        findById(chargeId).ifPresent(charge -> {
            String status = charge.getStatus();
            if (oldStatuses.contains(ChargeStatus.chargeStatusFrom(status))) {
                updateStatus(chargeId, newStatus);
                updated[0] = 1;
            }
        });
        return updated[0];
    }

    @Override
    public Optional<String> findAccountByTransactionId(String provider, String transactionId) {
        String qlString = "SELECT c.gatewayAccount.id FROM ChargeEntity c " +
                "WHERE " +
                "c.gatewayTransactionId=:gatewayTransactionId " +
                "AND " +
                "c.gatewayAccount.gatewayName=:provider";

        Query query = entityManager.get().createQuery(qlString);
        query.setParameter("gatewayTransactionId", transactionId);
        query.setParameter("provider", provider);

        String account = null;
        List<Long> result = query.getResultList();

        if (!result.isEmpty()) {
            account = result.get(0).toString();
        }

        return Optional.ofNullable(account);
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

    //FIXME This will be removed shortly after finishing PP-576 refactoring
    public void mergeChargeEntityWithChangedStatus(ChargeEntity chargeEntity) {
        super.merge(chargeEntity);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, ChargeStatus.chargeStatusFrom(chargeEntity.getStatus()), LocalDateTime.now()));
    }

    private Map<String, Object> buildChargeMap(ChargeEntity chargeEntity) {
        Map<String, Object> charge = new HashMap<>();
        charge.put("charge_id", String.valueOf(chargeEntity.getId()));
        charge.put("amount", chargeEntity.getAmount());
        charge.put("status", chargeEntity.getStatus());
        charge.put("gateway_transaction_id", chargeEntity.getGatewayTransactionId());
        charge.put("return_url", chargeEntity.getReturnUrl());
        charge.put("gateway_account_id", String.valueOf(chargeEntity.getGatewayAccount().getId()));
        charge.put("description", chargeEntity.getDescription());
        charge.put("reference", chargeEntity.getReference());
        charge.put("payment_provider", chargeEntity.getGatewayAccount().getGatewayName());
        charge.put("created_date", chargeEntity.getCreatedDate());
        return charge;
    }
}
