package uk.gov.pay.connector.chargeevent.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.charge.model.domain.ChargeStatus;
import uk.gov.pay.connector.chargeevent.model.domain.ChargeEventEntity;
import uk.gov.pay.connector.common.dao.JpaDao;

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

    public void persistChargeEventOf(ChargeEntity chargeEntity) {
        this.persistChargeEventOf(chargeEntity, null);
    }

    public ChargeEventEntity persistChargeEventOf(ChargeEntity chargeEntity, ZonedDateTime gatewayEventDate) {
        ChargeEventEntity chargeEventEntity = ChargeEventEntity.from(chargeEntity, ChargeStatus.fromString(chargeEntity.getStatus()),
                Optional.ofNullable(gatewayEventDate));
        this.persist(chargeEventEntity);
        this.flush();
        this.forceRefresh(chargeEventEntity);
        return chargeEventEntity;
    }

    public List<ChargeEventEntity> findChargeEvents(ZonedDateTime startDate, ZonedDateTime endDate, int page, int size) {
        String query = "SELECT ce FROM ChargeEventEntity ce " +
                "WHERE ce.updated >= :startDate and ce.updated <= :endDate" +
                " order by ce.updated asc";

        int firstResult = (page - 1) * size;

        return entityManager.get()
                .createQuery(query, ChargeEventEntity.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setFirstResult(firstResult)
                .setMaxResults(size)
                .getResultList();
    }
}
