package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static uk.gov.pay.connector.model.domain.ChargeStatus.CREATED;

@Transactional
public class ChargeDao extends JpaDao<ChargeEntity> {

    private EventDao eventDao;

    @Inject
    public ChargeDao(final Provider<EntityManager> entityManager, EventDao eventDao) {
        super(entityManager);
        this.eventDao = eventDao;
    }

    public List<ChargeEntity> findAllBy(ChargeSearch searchQuery) {
        TypedQuery<ChargeEntity> query = searchQuery.apply(entityManager.get());
        return query.getResultList();
    }

    public Optional<ChargeEntity> findById(Long chargeId) {
        return super.findById(ChargeEntity.class, chargeId);
    }

    public Optional<ChargeEntity> findByIdAndGatewayAccount(Long chargeId, Long accountId) {
        return findById(chargeId).filter(charge -> charge.isAssociatedTo(accountId));
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

    // Temporary methods until notification listeners are in place
    public void persist(ChargeEntity chargeEntity) {
        super.persist(chargeEntity);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, CREATED, chargeEntity.getCreatedDate().toLocalDateTime()));
    }

    public void mergeAndNotifyStatusHasChanged(ChargeEntity chargeEntity) {
        super.merge(chargeEntity);
        eventDao.persist(ChargeEventEntity.from(chargeEntity, ChargeStatus.chargeStatusFrom(chargeEntity.getStatus()), LocalDateTime.now()));
    }
}
