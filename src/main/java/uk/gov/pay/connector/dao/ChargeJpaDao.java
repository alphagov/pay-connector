package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.*;
import uk.gov.pay.connector.util.ChargeEventJpaListener;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class ChargeJpaDao extends JpaDao<ChargeEntity> implements IChargeDao {

    private static final Logger logger = LoggerFactory.getLogger(ChargeJpaDao.class);

    private ChargeEventJpaListener eventListener;
    private GatewayAccountJpaDao gatewayAccountDao;

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager, ChargeEventJpaListener eventListener, GatewayAccountJpaDao gatewayAccountDao) {
        super(entityManager);
        this.eventListener = eventListener;
        this.gatewayAccountDao = gatewayAccountDao;
    }

    @Transactional
    public void create(ChargeEntity charge) {
        super.persist(charge);
        eventListener.notify(ChargeEventEntity.from(charge, CREATED, charge.getCreatedDate().toLocalDateTime()));
    }

    @Transactional
    public ChargeEntity update(final ChargeEntity charge) {
        ChargeEntity updated = super.merge(charge);
        eventListener.notify(ChargeEventEntity.from(
                charge,
                ChargeStatus.chargeStatusFrom(charge.getStatus()),
                LocalDateTime.now()));
        return updated;
    }

    public Optional<ChargeEntity> findByGatewayTransactionIdAndProvider(String transactionId, String paymentProvider) {
        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery("select c from ChargeEntity c where c.gatewayTransactionId = :gatewayTransactionId and c.gatewayAccount.gatewayName = :paymentProvider", ChargeEntity.class);

        query.setParameter("gatewayTransactionId", transactionId);
        query.setParameter("paymentProvider", paymentProvider);

        return Optional.ofNullable(query.getSingleResult());
    }

    public List<ChargeEntity> findAllBy(ChargeSearchQuery searchQuery) {
        TypedQuery<ChargeEntity> query = searchQuery.apply(entityManager.get());
        return query.getResultList();
    }

    @Override
    @Transactional
    public String saveNewCharge(String gatewayAccountId, Map<String, Object> charge) {
        GatewayAccountEntity gatewayAccountEntity = gatewayAccountDao.findById(new Long(gatewayAccountId))
                .orElseThrow(() -> new PayDBIException(format("Could not create a new charge with Gateway accountId '%s'", gatewayAccountId, 0)));

        ChargeEntity chargeEntity =
                new ChargeEntity(new Long(charge.get("amount").toString()),
                        CREATED.getValue(),
                        null,
                        charge.get("return_url").toString(),
                        charge.get("description").toString(),
                        charge.get("reference").toString(),
                        gatewayAccountEntity);

        create(chargeEntity);
        return chargeEntity.getId().toString();
    }

    @Override
    public Optional<Map<String, Object>> findChargeForAccount(String chargeId, String accountId) {
        Optional<ChargeEntity> chargeEntityOpt = findById(new Long(chargeId));
        if (chargeEntityOpt.isPresent() && chargeEntityOpt.get().getGatewayAccount().getId().equals(accountId)) {
            return Optional.of(buildChargeMap(chargeEntityOpt.get()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Map<String, Object>> findById(String chargeId) {
        Map<String, Object> chargeMap = new HashMap<>();
        Optional<ChargeEntity> chargeEntityOptional = findById(new Long(chargeId));

        if (chargeEntityOptional.isPresent()) {
            chargeMap = buildChargeMap(chargeEntityOptional.get());
        }
        return Optional.of(chargeMap);
    }

    @Override
    public Optional<ChargeEntity> findById(Long chargeId) {
        return super.findById(ChargeEntity.class, chargeId);
    }

    @Override
    @Transactional
    public void updateGatewayTransactionId(String chargeId, String transactionId) {
        ChargeEntity charge = findById(new Long(chargeId))
                .orElseThrow(() -> new PayDBIException(format("Could not update charge '%s' with gateway transaction id %s", chargeId, transactionId)));
        charge.setGatewayTransactionId(transactionId);
    }

    @Override
    @Transactional
    public void updateStatusWithGatewayInfo(String provider, String gatewayTransactionId, ChargeStatus newStatus) {

        final int[] updated = {0};

        findByGatewayTransactionIdAndProvider(gatewayTransactionId, provider)
                .ifPresent(chargeEntity -> {
                    chargeEntity.setStatus(newStatus);
                    updated[0] = 1;
                });

        if (updated[0] != 1) {
            throw new PayDBIException(format("Could not update charge (gateway_transaction_id: %s) with status %s, updated %d rows.", gatewayTransactionId, newStatus, updated));
        }

        ChargeEntity charge = findChargeByTransactionId(provider, gatewayTransactionId);
        if (charge != null) {
            eventListener.notify(ChargeEventEntity.from(charge, newStatus, LocalDateTime.now()));
        } else {
            logger.error(String.format("Cannot find id for gateway_transaction_id [%s] and provider [%s]", gatewayTransactionId, provider));
        }
    }

    @Override
    public List<Map<String, Object>> findAllBy(String gatewayAccountId, String reference, ExternalChargeStatus status, String fromDate, String toDate) {

        ChargeSearchQuery searchQuery = new ChargeSearchQuery(new Long(gatewayAccountId));

        if (reference != null) {
            searchQuery.withReferenceLike(reference);
        }
        if (status != null) {
            searchQuery.withStatusIn(status.getInnerStates());
        }
        if (fromDate != null) {
            searchQuery.withCreatedDateFrom(ZonedDateTime.parse(fromDate));
        }
        if (toDate != null) {
            searchQuery.withCreatedDateTo(ZonedDateTime.parse(toDate));
        }

        List<ChargeEntity> chargeEntities = findAllBy(searchQuery);
        logger.info("found " + chargeEntities.size() + " charge records for the criteria");

        return chargeEntities
                .stream()
                .map(charge -> buildChargeMap(charge))
                .collect(toList());
    }

    @Override
    @Transactional
    public void updateStatus(String chargeId, ChargeStatus newStatus) {
        updateStatus(new Long(chargeId), newStatus);
    }

    @Transactional
    public ChargeEntity updateStatus(Long chargeId, ChargeStatus newStatus) {
        ChargeEntity chargeEntity = findById(chargeId)
                .orElseThrow(() -> new PayDBIException(format("Could not update charge '%s' with status %s, updated %d rows.", chargeId, newStatus, 0)));
        chargeEntity.setStatus(newStatus);
        eventListener.notify(ChargeEventEntity.from(chargeEntity, newStatus, LocalDateTime.now()));
        return chargeEntity;
    }

    @Override
    @Transactional
    public int updateNewStatusWhereOldStatusIn(String chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {
        return updateNewStatusWhereOldStatusIn(new Long(chargeId), newStatus, oldStatuses);
    }

    @Transactional
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

        String account = query.getSingleResult().toString();
        return Optional.ofNullable(account);
    }

    private Map<String, Object> buildChargeMap(ChargeEntity chargeEntity) {
        HashMap<String, Object> charge = new HashMap<>();
        charge.put("charge_id", chargeEntity.getId());
        charge.put("amount", chargeEntity.getAmount());
        charge.put("status", chargeEntity.getStatus());
        charge.put("gateway_transaction_id", chargeEntity.getGatewayTransactionId());
        charge.put("return_url", chargeEntity.getReturnUrl());
        charge.put("gateway_account_id", String.valueOf(chargeEntity.getGatewayAccount().getId()));
        charge.put("description", chargeEntity.getDescription());
        charge.put("reference", chargeEntity.getReference());
        charge.put("created_date", chargeEntity.getCreatedDate());
        return Collections.unmodifiableMap(charge);
    }

    private ChargeEntity findChargeByTransactionId(String provider, String transactionId) {
        String qlString = "SELECT c FROM ChargeEntity c " +
                "WHERE " +
                "c.gatewayAccount.gatewayName=:provider " +
                "AND " +
                "c.gatewayTransactionId=:transactionId";

        TypedQuery<ChargeEntity> query = entityManager.get().createQuery(qlString, ChargeEntity.class);
        query.setParameter("provider", provider);
        query.setParameter("transactionId", transactionId);
        return query.getSingleResult();
    }

}


