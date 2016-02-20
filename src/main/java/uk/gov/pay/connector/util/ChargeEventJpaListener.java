package uk.gov.pay.connector.util;

import com.google.inject.Inject;
import uk.gov.pay.connector.dao.EventJpaDao;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

public class ChargeEventJpaListener {

    @Inject
    private EventJpaDao eventDao;

    public void notify(ChargeEventEntity chargeEvent) {
        eventDao.persist(chargeEvent);
    }
}
