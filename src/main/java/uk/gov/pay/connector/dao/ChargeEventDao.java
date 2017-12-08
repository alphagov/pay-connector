package uk.gov.pay.connector.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.model.domain.ChargeEntity;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Transactional
public class ChargeEventDao extends JpaDao<ChargeEventEntity> {

    @Inject
    public ChargeEventDao(final Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public void persistChargeEventOf(ChargeEntity chargeEntity, Optional<ZonedDateTime> gatewayEventDate) {
        this.persist(ChargeEventEntity.from(chargeEntity, ChargeStatus.fromString(chargeEntity.getStatus()),
                ZonedDateTime.now(), gatewayEventDate));
    }

    public List<ChargeEventEntity> findWithLimit(int limitSize) {
        return entityManager.get()
                .createQuery("SELECT c FROM ChargeEventEntity c", ChargeEventEntity.class)
                .setMaxResults(limitSize)
                .getResultList();
    }
}
