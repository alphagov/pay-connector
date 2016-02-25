package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.stream.Collectors;

public class EventJpaDao extends JpaDao<ChargeEventEntity> implements IEventDao {

    @Inject
    public EventJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    @Transactional
    public List<ChargeEvent> findEvents(Long accountId, Long chargeId) {
        return entityManager.get().createQuery(
                "SELECT cs " +
                        "FROM ChargeEventEntity AS cs WHERE cs.chargeEntity.id IN " +
                        "(SELECT ch.id FROM ChargeEntity AS ch" +
                        " WHERE ch.id=:chargeId AND" +
                        " ch.gatewayAccount.id=:accountId)", ChargeEventEntity.class)
                .setParameter("chargeId", chargeId)
                .setParameter("accountId", accountId)
                .getResultList().stream()
                .map(entity -> new ChargeEvent(entity.getId(), entity.getStatus().toString(), entity.getUpdated()))
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ChargeEventEntity> findEventsEntities(Long accountId, Long chargeId) {
        return entityManager.get().createQuery(
                "SELECT cs " +
                        "FROM ChargeEventEntity AS cs WHERE cs.chargeEntity.id IN " +
                        "(SELECT ch.id FROM ChargeEntity AS ch" +
                        " WHERE ch.id=:chargeId AND" +
                        " ch.gatewayAccount.id=:accountId)", ChargeEventEntity.class)
                .setParameter("chargeId", chargeId)
                .setParameter("accountId", accountId)
                .getResultList();
    }

    @Override
    @Transactional
    public void save(ChargeEvent chargeEvent) {
        entityManager.get().persist(chargeEvent);
        entityManager.get().flush();
    }
}
