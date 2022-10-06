package uk.gov.pay.connector.events;

import uk.gov.pay.connector.app.config.EmittedEventSweepConfig;
import uk.gov.pay.connector.events.dao.EmittedEventDao;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

class EmittedEventBatchIterator implements Iterator<EmittedEventBatchIterator.EventBatch> {
    private final EmittedEventDao emittedEventDao;
    private final EmittedEventSweepConfig sweepConfig;
    private Long currentBatchStartId;
    private final int batchSize;
    private final ZonedDateTime batchStartTime;
    private EventBatch currentBatch;
    private Optional<Long> maybeMaximumIdOfEventsEligibleForReEmission;
    private Instant cutoffDate;

    EmittedEventBatchIterator(EmittedEventDao emittedEventDao,
                              EmittedEventSweepConfig sweepConfig,
                              Long startId,
                              int batchSize,
                              ZonedDateTime batchStartTime) {
        this.emittedEventDao = emittedEventDao;
        this.sweepConfig = sweepConfig;
        this.batchSize = batchSize;
        this.batchStartTime = batchStartTime;
        currentBatch = getFirstBatch(startId);
    }
    
    @Override
    public boolean hasNext() {
        return currentBatch != null && (! currentBatch.isEmpty());
    }

    @Override
    public EventBatch next() {
        final EventBatch batchToReturn = currentBatch;

        if ((!currentBatch.isEmpty()) && currentBatch.getEndId().isPresent()) {
            currentBatch = getNextBatch(currentBatch.getEndId().get());
        }

        return batchToReturn;
    }

    Optional<Long> getMaximumIdOfEventsEligibleForReEmission() {
        return maybeMaximumIdOfEventsEligibleForReEmission;
    }
    
    Long getCurrentBatchStartId() {
        return currentBatchStartId;
    }

    private EventBatch getFirstBatch(Long startFromId) {
        cutoffDate = getCutoffDateForProcessingNotEmittedEvents(batchStartTime);
        maybeMaximumIdOfEventsEligibleForReEmission = emittedEventDao.findNotEmittedEventMaxIdOlderThan(cutoffDate, batchStartTime);
        return getNextBatch(startFromId);
    }

    private EventBatch getNextBatch(Long startFromId) {
        final List<EmittedEventEntity> emittedEventEntities = maybeMaximumIdOfEventsEligibleForReEmission
                .map(maxId ->
                        emittedEventDao.findNotEmittedEventsOlderThan(cutoffDate, batchSize, startFromId, maxId, batchStartTime))
                .orElse(List.of());
        this.currentBatchStartId = startFromId;
        return new EventBatch(emittedEventEntities, startFromId);
    }

    private Instant getCutoffDateForProcessingNotEmittedEvents(ZonedDateTime batchStartTime) {
        int notEmittedEventMaxAgeInSeconds = sweepConfig.getNotEmittedEventMaxAgeInSeconds();
        return batchStartTime.minusSeconds(notEmittedEventMaxAgeInSeconds).toInstant();
    }
    

    public static class EventBatch {
        private final List<EmittedEventEntity> events;
        private final Long startId;

        EventBatch(List<EmittedEventEntity> events, Long startId) {
            this.events = events;
            this.startId = startId;
        }

        public List<EmittedEventEntity> getEvents() {
            return events;
        }

        public Long getStartId() {
            return startId;
        }

        public Optional<Long> getEndId() {
            return idOfLastInBatch();
        }
        
        public boolean isEmpty() {
            return events.isEmpty();
        }

        public Optional<Instant> oldestEventDate() {
            return events.stream().map(EmittedEventEntity::getEventDate).min(Instant::compareTo);
        }
        
        public Optional<EmittedEventEntity> last() {
            if (isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(events.get(events.size() - 1));
            }
        }
        
        public Optional<Long> idOfLastInBatch() {
            return last().map(EmittedEventEntity::getId);
        }
        
        public int getSize() {
            return events.size();
        }
    }
}
