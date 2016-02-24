package uk.gov.pay.connector.dao;

import uk.gov.pay.connector.model.domain.ChargeEvent;
import uk.gov.pay.connector.model.domain.ChargeEventEntity;

import java.util.List;

public interface IEventDao {

    List<ChargeEvent> findEvents(Long accountId, Long chargeId);
    default List<ChargeEventEntity> findEventsEntities(Long accountId, Long chargeId){
       throw new UnsupportedOperationException("find events entities not supported!!!");
    }
    void save(ChargeEvent chargeEvent);
}
