package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.LongMapper;
import uk.gov.pay.connector.model.ChargeStatus;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public long saveNewCharge(Map<String, Object> charge) {
        Map<String, Object> fixedCharge = copyAndConvertFieldToLong(charge, "gateway_account");
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount, gateway_account_id, status) VALUES (:amount, :gateway_account, :status)")
                                .bindFromMap(fixedCharge)
                                .bind("status", ChargeStatus.CREATED)
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

    public void updateStatus(long chargeId, ChargeStatus newStatus) throws PayDBIException {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET status=:status WHERE charge_id=:charge_id")
                                .bind("charge_id", chargeId)
                                .bind("status", newStatus.getValue())
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(String.format("Could not update charge '%s' with status %s", chargeId, newStatus));
        }
    }

    private Map<String, Object> copyAndConvertFieldToLong(Map<String, Object> charge, String field) {
        Map<String, Object> copy = newHashMap(charge);
        Long fieldAsLong = Long.valueOf(copy.remove(field).toString());
        copy.put(field, fieldAsLong);
        return copy;
    }
}
