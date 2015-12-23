package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import uk.gov.pay.connector.mappers.ChargeEventMapper;
import uk.gov.pay.connector.model.domain.ChargeEvent;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class EventDao {
    private DBI jdbi;

    public EventDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public List<ChargeEvent> findEvents(Long accountId, Long chargeId) {
        return jdbi.withHandle(handle ->
                handle.createQuery("SELECT cs.id, cs.charge_id, cs.status, to_char(cs.updated,'DD/MM/YYYY HH24:MI:SS') as updated FROM charge_events AS cs WHERE cs.charge_id IN " +
                        "(SELECT ch.charge_id FROM charges AS ch" +
                        " WHERE ch.charge_id=:chargeId AND" +
                        " ch.gateway_account_id=:accountId)")
                        .bind("chargeId", chargeId)
                        .bind("accountId", accountId)
                        .map(new ChargeEventMapper())
                        .list()
        );
    }

    public void save(ChargeEvent chargeEvent) {
        jdbi.withHandle(handle ->
                handle.createStatement("INSERT INTO charge_events(charge_id,status) values(:chargeId,:status)")
                        .bind("chargeId", chargeEvent.getChargeId())
                        .bind("status", chargeEvent.getStatus().getValue())
                        .execute()
        );
    }

}
