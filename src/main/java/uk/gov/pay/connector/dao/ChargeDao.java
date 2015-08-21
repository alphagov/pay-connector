package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.util.LongMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.util.jdbi.UuidMapper;

import java.util.UUID;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public UUID insertAmountAndReturnNewId(long amount) {
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount) VALUES (:amount)")
                                .bind("amount", amount)
                                .executeAndReturnGeneratedKeys(UuidMapper.FIRST)
                                .first()
        );
    }

    public long getAmountById(UUID chargeId) {
        return jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT amount FROM charges WHERE charge_id=(:charge_id)")
                                .bind("charge_id", chargeId)
                                .map(LongMapper.FIRST)
                                .first()
        );
    }
}
