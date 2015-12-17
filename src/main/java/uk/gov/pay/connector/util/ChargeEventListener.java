package uk.gov.pay.connector.util;

import uk.gov.pay.connector.dao.EventDao;
import uk.gov.pay.connector.model.domain.ChargeEvent;

public class ChargeEventListener {

    private EventDao eventDao;

    public ChargeEventListener(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    public void notify(ChargeEvent chargeEvent) {
        eventDao.save(chargeEvent);
    }
}
