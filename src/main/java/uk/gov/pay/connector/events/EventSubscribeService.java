package uk.gov.pay.connector.events;

import com.google.common.eventbus.Subscribe;
import uk.gov.pay.connector.dao.RefundedEventDao;
import uk.gov.pay.connector.dao.SuccessfulChargeEventDao;

import javax.inject.Inject;
import java.util.Optional;

public class EventSubscribeService {
    
    private final SuccessfulChargeEventDao successfulChargeEventDao;
    private final RefundedEventDao refundedEventDao;

    @Inject
    public EventSubscribeService(SuccessfulChargeEventDao successfulChargeEventDao, RefundedEventDao refundedEventDao) {
        this.successfulChargeEventDao = successfulChargeEventDao;
        this.refundedEventDao = refundedEventDao;
    }

    @Subscribe
    public void onEvent(SuccessfulChargeEvent event) {
        Optional<SuccessfulChargeEvent> result = successfulChargeEventDao.findById(SuccessfulChargeEvent.class, event.getExternalId());
        if (!result.isPresent()) successfulChargeEventDao.persist(event);
    }
    
    @Subscribe
    public void onEvent(RefundedEvent event) {
        Optional<RefundedEvent> result = refundedEventDao.findById(RefundedEvent.class, event.getExternalId());
        if (!result.isPresent()) refundedEventDao.persist(event);
    }
}
