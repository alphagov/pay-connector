package uk.gov.pay.connector.events.dao;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import uk.gov.pay.connector.common.dao.JpaDao;
import uk.gov.pay.connector.events.EmittedEventEntity;
import uk.gov.pay.connector.events.model.Event;

import javax.persistence.EntityManager;
import java.time.ZonedDateTime;
import java.util.List;

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

    public void recordEmission(Event event) {
        final EmittedEventEntity emittedEvent = new EmittedEventEntity(event.getResourceType().getLowercase(),
                event.getResourceExternalId(),
                event.getEventType(),
                event.getTimestamp(),
                ZonedDateTime.now()
        );

        persist(emittedEvent);
    }
}
