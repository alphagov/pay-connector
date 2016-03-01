package uk.gov.pay.connector.util;

import com.google.inject.Inject;
import uk.gov.pay.connector.dao.IEventDao;
import uk.gov.pay.connector.model.domain.ChargeEvent;

public class ChargeEventListener {

    private IEventDao eventDao;

    @Inject
    public ChargeEventListener(IEventDao eventDao) {
        this.eventDao = eventDao;
    }

    public void notify(ChargeEvent chargeEvent) {
        eventDao.save(chargeEvent);
    }
}
