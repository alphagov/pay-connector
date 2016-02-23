package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import static java.lang.String.format;
import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

public class ChargeJpaDao extends JpaDao<ChargeEntity> {

    private static final Logger logger = LoggerFactory.getLogger(ChargeJpaDao.class);

    private ChargeEventJpaListener eventListener;

    @Inject
    public ChargeJpaDao(final Provider<EntityManager> entityManager, ChargeEventJpaListener eventListener) {
        super(entityManager);
        this.eventListener = eventListener;
    }

    @Transactional
    public void persist(ChargeEntity charge) {
        super.persist(charge);
        eventListener.notify(ChargeEventEntity.from(charge, CREATED, charge.getCreatedDate().toLocalDateTime()));
    }

    @Transactional
    public ChargeEntity merge(final ChargeEntity charge) {
        ChargeEntity updated = super.merge(charge);
        eventListener.notify(ChargeEventEntity.from(
                charge,
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

    public List<ChargeEntity> findAllBy(ChargeSearchQuery searchQuery) {
        TypedQuery<ChargeEntity> query = searchQuery.apply(entityManager.get());
        return query.getResultList();
    }

    @Transactional
    public int updateNewStatusWhereOldStatusIn(Long chargeId, ChargeStatus newStatus, List<ChargeStatus> oldStatuses) {

        //FIXME WIP. This method won't exist as soon this is switched to use JPA.
        // Just done to conform to common interface before switching dao implementations
        final int[] updated = {0};

        findById(chargeId).ifPresent(charge -> {
            String status = charge.getStatus();
            if (oldStatuses.contains(ChargeStatus.chargeStatusFrom(status))) {
                charge.setStatus(newStatus);
                eventListener.notify(ChargeEventEntity.from(charge, newStatus, LocalDateTime.now()));
            }

            updated[0] = 1;
        });

        return updated[0];
    }

    @Transactional
    public void updateStatus(Long chargeId, ChargeStatus newStatus) {
        ChargeEntity chargeEntity = findById(chargeId)
                .orElseThrow(() -> new PayDBIException(format("Could not update charge '%s' with status %s, updated %d rows.", chargeId, newStatus, 0)));
        chargeEntity.setStatus(newStatus);
        eventListener.notify(ChargeEventEntity.from(chargeEntity, newStatus, LocalDateTime.now()));
    }
}


