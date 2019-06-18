package uk.gov.pay.connector.tasks;

import com.google.inject.persist.Transactional;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.MDC;
import uk.gov.pay.connector.charge.dao.ChargeDao;
import uk.gov.pay.connector.charge.model.domain.ChargeEntity;
import uk.gov.pay.connector.events.PaymentCreatedEvent;
import uk.gov.pay.connector.events.dao.EmittedEventDao;

import javax.inject.Inject;
import java.util.Optional;

import static uk.gov.pay.connector.filters.RestClientLoggingFilter.HEADER_REQUEST_ID;

public class HistoricalEventEmitterWorker {


    private final ChargeDao chargeDao;
    private final EmittedEventDao emittedEventDao;

    @Inject
    public HistoricalEventEmitterWorker(ChargeDao chargeDao, EmittedEventDao emittedEventDao) {
        this.chargeDao = chargeDao;
        this.emittedEventDao = emittedEventDao;
    }

    public void execute(Long startId) {
        MDC.put(HEADER_REQUEST_ID, "HistoricalEventEmitterWorker starting" + RandomUtils.nextLong(0, 10000));

        Long maxId = chargeDao.findMaxId();
        for (long i = startId; i <= maxId; i++) {
            emitEventFor(i);
        }
    }

    // needs to be public for transactional annotation
    @Transactional
    public void emitEventFor(long i) {
        final Optional<ChargeEntity> maybeCharge = chargeDao.findById(i);
        
        maybeCharge
                .map(PaymentCreatedEvent::from)
                .filter(this::hasNotBeenEmittedBefore)
                .ifPresent(this::persistAndEmit);
    }

    private boolean hasNotBeenEmittedBefore(PaymentCreatedEvent paymentCreatedEvent) {
        emittedEventDao.hasBeenEmittedBefore(paymentCreatedEvent);
        return false;
    }

    private void persistAndEmit(PaymentCreatedEvent event) {
        
    }

    private PaymentCreatedEvent eventFor(ChargeEntity c) {
        return null;
    }
}
