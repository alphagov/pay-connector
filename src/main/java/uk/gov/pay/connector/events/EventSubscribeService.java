package uk.gov.pay.connector.events;

import com.google.common.eventbus.Subscribe;
import uk.gov.pay.connector.dao.SuccessfulChargeEventDao;

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;

public class EventSubscribeService {
    
    private final SuccessfulChargeEventDao dao;

    @Inject
    public EventSubscribeService(SuccessfulChargeEventDao dao) {
        this.dao = dao;
    }

    @Subscribe
    public void onEvent(SuccessfulChargeEvent event) {
        dao.findById(SuccessfulChargeEvent.class, event.getExternalId()).ifPresent(successfulChargeEvent -> dao.persist(successfulChargeEvent));
    }
}
