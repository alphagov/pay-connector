package uk.gov.pay.connector.dao;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.DefaultMapper;
import org.skife.jdbi.v2.util.StringMapper;
import uk.gov.pay.connector.model.domain.ChargeStatus;

import java.util.Map;
import java.util.Optional;

import static com.google.common.collect.Maps.newHashMap;
import static java.lang.String.format;

public class ChargeDao {
    private DBI jdbi;

    public ChargeDao(DBI jdbi) {
        this.jdbi = jdbi;
    }

    public String saveNewCharge(Map<String, Object> charge) throws PayDBIException {
        Map<String, Object> fixedCharge = copyAndConvertFieldToLong(charge, "gateway_account_id");
        return jdbi.withHandle(handle ->
                        handle
                                .createStatement("INSERT INTO charges(amount, gateway_account_id, status, return_url) " +
                                        "VALUES (:amount, :gateway_account_id, :status, :return_url)")
                                .bindFromMap(fixedCharge)
                                .bind("status", ChargeStatus.CREATED.getValue())
                                .executeAndReturnGeneratedKeys(StringMapper.FIRST)
                                .first()
        );
    }

    public Optional<Map<String, Object>> findById(String chargeId) {
        Map<String, Object> data = jdbi.withHandle(handle ->
                        handle
                                .createQuery("SELECT charge_id, amount, gateway_account_id, status, return_url, gateway_transaction_id " +
                                        "FROM charges WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .map(new DefaultMapper())
                                .first()
        );

        if (data != null) {
            data = copyAndConvertFieldsToString(data, "charge_id", "gateway_account_id");
        }
        return Optional.ofNullable(data);
    }

    public void updateStatus(String chargeId, ChargeStatus newStatus) {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET status=:status WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("status", newStatus.getValue())
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(format("Could not update charge '%s' with status %s", chargeId, newStatus));
        }
    }

    private Map<String, Object> copyAndConvertFieldToLong(Map<String, Object> charge, String field) {
        Map<String, Object> copy = newHashMap(charge);
        Long fieldAsLong = Long.valueOf(copy.remove(field).toString());
        copy.put(field, fieldAsLong);
        return copy;
    }

    private Map<String, Object> copyAndConvertFieldsToString(Map<String, Object> data, String... fields) {
        Map<String, Object> copy = newHashMap(data);
        for (String field : fields) {
            copy.put(field, String.valueOf(copy.remove(field)));
        }
        return copy;
    }

    public void updateGatewayTransactionId(String chargeId, String transactionId) throws PayDBIException {
        Integer numberOfUpdates = jdbi.withHandle(handle ->
                        handle
                                .createStatement("UPDATE charges SET gateway_transaction_id=:transactionId WHERE charge_id=:charge_id")
                                .bind("charge_id", Long.valueOf(chargeId))
                                .bind("transactionId", transactionId)
                                .execute()
        );

        if (numberOfUpdates != 1) {
            throw new PayDBIException(String.format("Could not update charge '%s' with gateway transaction id %s", chargeId, transactionId));
        }
    }
}
