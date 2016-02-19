package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

public class EventJpaDao extends JpaDao<ChargeEventEntity> {

    @Inject
    public EventJpaDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }
    public List<ChargeEventEntity> findEvents(Long accountId, Long chargeId) {
        return entityManager.get().createQuery(
                "SELECT cs " +
                        "FROM ChargeEventEntity AS cs WHERE cs.chargeId IN " +
                        "(SELECT ch.id FROM ChargeEntity AS ch" +
                        " WHERE ch.id=:chargeId AND" +
                        " ch.gatewayAccount.id=:accountId)", ChargeEventEntity.class)
                        .setParameter("chargeId", chargeId)
                        .setParameter("accountId", accountId)
                        .getResultList();
    }
}
