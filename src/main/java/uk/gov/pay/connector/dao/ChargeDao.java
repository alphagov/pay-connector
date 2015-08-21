package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.LongMapper;
import uk.gov.pay.connector.util.jdbi.UuidMapper;

import java.util.Map;
import java.util.UUID;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public long saveNewCharge(Map<String, Object> charge) {
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount, gateway_account_id, status) VALUES (:amount, :gateway_account, 'CREATED')")
                                .bindFromMap(charge)
                                .executeAndReturnGeneratedKeys(LongMapper.FIRST)
                                .first()
        );
    }

    public Map<String, Object> findById(long chargeId) {
        return jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT amount, gateway_account_id, status FROM charges WHERE charge_id=:charge_id")
                                .bind("charge_id", chargeId)
                                .map(new DefaultMapper())
                                .first()
        );
    }
}
