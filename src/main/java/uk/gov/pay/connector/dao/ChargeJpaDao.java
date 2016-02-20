package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.pay.connector.model.api.ExternalChargeStatus;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;
import uk.gov.pay.connector.util.ChargeEventJpaListener;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;
import static uk.gov.pay.connector.util.ChargesJpaSearch.constructSearchTransactionsQuery;
import static uk.gov.pay.connector.util.ChargesJpaSearch.setParameters;

public class ChargeJpaDao extends JpaDao<ChargeEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ChargeDao.class);

    private ChargeEventJpaListener eventListener;

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager, ChargeEventJpaListener eventListener) {
        super(entityManager);
        this.eventListener = eventListener;
    }

    @Transactional
    public void persist( ChargeEntity charge) {
        super.persist(charge);
        entityManager.get().flush();
        entityManager.get().clear();
        eventListener.notify(ChargeEventEntity.from(charge.getId(), CREATED, charge.getCreatedDate().toLocalDateTime()));
    }

    @Transactional
    public ChargeEntity merge(final ChargeEntity charge) {
        ChargeEntity updated = super.merge(charge);
        entityManager.get().flush();
        entityManager.get().clear();
        eventListener.notify(ChargeEventEntity.from(
                charge.getId(),
                ChargeStatus.chargeStatusFrom(charge.getStatus()),
                LocalDateTime.now()));
        return updated;
    }

    public <ID> Optional<ChargeEntity> findById(final ID id) {
        return super.findById(ChargeEntity.class, id);
    }


    public Optional<ChargeEntity> findByGatewayTransactionIdAndProvider(String transactionId, String paymentProvider) {
        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery("select c from ChargeEntity c where c.gatewayTransactionId = :gatewayTransactionId and c.gatewayAccount.gatewayName = :paymentProvider", ChargeEntity.class);

        query.setParameter("gatewayTransactionId", transactionId);
        query.setParameter("paymentProvider", paymentProvider);

        return Optional.ofNullable(query.getSingleResult());
    }

    // updates the new status only if the charge is in one of the old statuses and returns num of rows affected
    // very specific transition happening here so check for a valid state before transitioning
    @Transactional
    public int updateNewStatusWhereOldStatusIn(Long chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {
        String sql = format("UPDATE ChargeEntity c SET c.status=:newStatus WHERE c.id=:chargeId and c.status in (%s)", getStringFromStatusList(oldStatuses));

        int updateCount = entityManager.get().createQuery(sql, ChargeEntity.class)
               .setParameter("chargeId", chargeId)
               .setParameter("newStatus", newStatus.getValue())
               .executeUpdate();
//        entityManager.get().flush();
//        entityManager.get().clear();
        if (updateCount > 0) {
            eventListener.notify(ChargeEventEntity.from(chargeId, newStatus, LocalDateTime.now()));
        }
        return updateCount;
    }

    @Transactional
    public void updateStatus(Long chargeId, ChargeStatus newStatus) {

        int updateCount = entityManager.get()
                .createQuery("UPDATE ChargeEntity c SET c.status=:newStatus WHERE c.id=:chargeId", ChargeEntity.class)
                .setParameter("chargeId", chargeId)
                .setParameter("newStatus", newStatus.getValue())
                .executeUpdate();
        if (updateCount != 1) {
            throw new PayDBIException(format("Could not update charge '%s' with status %s, updated %d rows.", chargeId, newStatus, updateCount));
        }
        entityManager.get().flush();
        entityManager.get().clear();
        eventListener.notify(ChargeEventEntity.from(chargeId, newStatus, LocalDateTime.now()));
    }

	@Transactional
    public List<ChargeEntity> findAllBy(Long gatewayAccountId, String reference, ExternalChargeStatus status,
                                               String fromDate, String toDate) {
        String queryStr =
                "SELECT c " +
                        "FROM " +
                        "ChargeEntity c " +
                        "WHERE " +
                        "c.gatewayAccount.id=:gid " +
                        "%s " +
                        "ORDER BY c.id DESC";

        TypedQuery<ChargeEntity> query = entityManager.get()
                .createQuery(format(queryStr, constructSearchTransactionsQuery(reference, status, fromDate, toDate)), ChargeEntity.class);
        return setParameters(query, gatewayAccountId, reference, fromDate, toDate).getResultList();
    }

    private String getStringFromStatusList(List<ChargeStatus> oldStatuses) {
        return oldStatuses
                .stream()
                .map(t -> "'" + t.getValue() + "'")
                .collect(Collectors.joining(","));
    }

}
