package uk.gov.pay.connector.events.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.events.EmittedEventEntity;
import uk.gov.pay.connector.events.model.Event;
import uk.gov.pay.connector.events.model.ResourceType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Transactional
public class EmittedEventDao extends JpaDao<EmittedEventEntity> {

    @Inject
    protected EmittedEventDao(Provider<EntityManager> entityManager) {
        super(entityManager);
    }

    public boolean hasBeenEmittedBefore(Event event) {
        final List<Integer> singleResult = entityManager.get()
                .createQuery("select 1 from EmittedEventEntity e where " +
                        "e.resourceType = :resource_type AND " +
                        "e.eventDate = :event_date AND " +
                        "e.resourceExternalId = :resource_external_id AND " +
                        "e.eventType = :event_type", Integer.class)
                .setMaxResults(1)
                .setParameter("resource_type", event.getResourceType().getLowercase())
                .setParameter("resource_external_id", event.getResourceExternalId())
                .setParameter("event_type", event.getEventType())
                .setParameter("event_date", event.getTimestamp())
                .getResultList();
        return !singleResult.isEmpty();
    }

    public void recordEmission(Event event, ZonedDateTime doNotRetryEmitUntilDate) {
        final EmittedEventEntity emittedEvent = new EmittedEventEntity(event.getResourceType().getLowercase(),
                event.getResourceExternalId(),
                event.getEventType(),
                event.getTimestamp(),
                Instant.now(),
                doNotRetryEmitUntilDate
        );

        persist(emittedEvent);
    }

    public void recordEmission(ResourceType resourceType, String externalId, String eventType, Instant eventDate,
                               ZonedDateTime doNotRetryEmitUntil) {
        final EmittedEventEntity emittedEvent = new EmittedEventEntity(
                resourceType.getLowercase(),
                externalId,
                eventType,
                eventDate,
                null,
                doNotRetryEmitUntil
        );
        persist(emittedEvent);
    }

    @Transactional
    public void markEventAsEmitted(Event event) {
        Query query = entityManager.get()
                .createQuery("UPDATE EmittedEventEntity e" +
                        " SET e.emittedDate = :emittedDate , e.eventDate = :eventDate " +
                        " WHERE e.resourceType = :resource_type" +
                        " AND e.resourceExternalId = :resource_external_id" +
                        " AND e.eventType = :event_type" +
                        " AND e.emittedDate is null"
                );
        query.setParameter("resource_type", event.getResourceType().getLowercase())
                .setParameter("resource_external_id", event.getResourceExternalId())
                .setParameter("event_type", event.getEventType())
                .setParameter("emittedDate", Instant.now())
                .setParameter("eventDate", event.getTimestamp());

        query.executeUpdate();
    }

    public Optional<Long> findNotEmittedEventMaxIdOlderThan(Instant cutOffDate, ZonedDateTime now) {
        String query = "SELECT MAX(e.id) from EmittedEventEntity e " +
                "WHERE e.eventDate < :cutOffDate " +
                "AND e.emittedDate is null " +
                "AND (e.doNotRetryEmitUntil is null or e.doNotRetryEmitUntil < :currentDate)";

        return Optional.ofNullable(entityManager.get()
                .createQuery(query, Long.class)
                .setParameter("cutOffDate", cutOffDate)
                .setParameter("currentDate", now)
                .getSingleResult());
    }

    public List<EmittedEventEntity> findNotEmittedEventsOlderThan(Instant cutOffDate, int size,
                                                                  Long lastProcessedId, Long maxId,
                                                                  ZonedDateTime now) {
        String query = "SELECT e from EmittedEventEntity e " +
                "WHERE e.id > :lastProcessedId AND e.id <= :maxId AND e.eventDate < :cutOffDate " +
                "AND e.emittedDate is null " +
                "AND (e.doNotRetryEmitUntil is null or e.doNotRetryEmitUntil < :currentDate) " +
                "ORDER BY e.id";

        return entityManager.get()
                .createQuery(query, EmittedEventEntity.class)
                .setParameter("cutOffDate", cutOffDate)
                .setParameter("currentDate", now)
                .setParameter("lastProcessedId", lastProcessedId)
                .setParameter("maxId", maxId)
                .setMaxResults(size)
                .getResultList();
    }
}
