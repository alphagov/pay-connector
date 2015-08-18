package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.util.LongMapper;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public long insertAmountAndReturnNewId(long amount) {
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount) VALUES (:amount)")
                                .bind("amount", amount)
                                .executeAndReturnGeneratedKeys(LongMapper.FIRST)
                                .first()
        );
    }

    public long getAmountById(long chargeId) {
        return jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT amount FROM charges WHERE charge_id=:charge_id")
                                .bind("charge_id", chargeId)
                                .map(LongMapper.FIRST)
                                .first()
        );
    }
}
